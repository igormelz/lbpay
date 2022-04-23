/*
 * Copyright 2021,2022 OpenFS.RU
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.openfs.lbpay;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.ext.web.client.predicate.ErrorConverter;
import io.vertx.mutiny.ext.web.client.predicate.ResponsePredicate;
import ru.openfs.lbpay.model.AuditRecord;

@Path("/pay/dreamkas")
public class DreamkasResource {
    private WebClient client;

    @ConfigProperty(name = "dreamkas.host", defaultValue = "kabinet.dreamkas.ru")
    String host;

    @ConfigProperty(name = "dreamkas.port", defaultValue = "443")
    int port;

    @ConfigProperty(name = "dreamkas.ssl", defaultValue = "true")
    Boolean useSsl;

    @ConfigProperty(name = "dreamkas.token", defaultValue = "auth-token")
    String token;

    @ConfigProperty(name = "dreamkas.deviceId", defaultValue = "99999")
    int deviceId;

    @Inject
    AuditRepository audit;

    @Inject
    Vertx vertx;

    @Inject
    EventBus bus;

    @PostConstruct
    void initialize() {
        this.client = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost(host).setDefaultPort(port).setIdleTimeout(3).setSsl(useSsl));
    }

    ErrorConverter converter = ErrorConverter.createFullBody(result -> {
        HttpResponse<Buffer> response = result.response();
        String err = response.bodyAsString();
        if (err != null && !err.isBlank()) {
            Log.error(err);
            return new RuntimeException(err);
        }
        Log.errorf("dreamkas error: %s", result.message());
        return new RuntimeException(result.message());
    });

    ResponsePredicate predicate = ResponsePredicate.create(ResponsePredicate.SC_SUCCESS, converter);

    @ConsumeEvent("receipt-sale")
    public void receiptSale(JsonObject receipt) {
        boolean isValid = false;
        if (receipt.containsKey("email") && receipt.getString("email")
                .matches("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$")) {
            isValid = true;
        } else if (receipt.containsKey("phone") && receipt.getString("phone").matches("^\\+?[1-9]\\d{10,13}+$")) {
            isValid = true;
            if (!receipt.getString("phone").startsWith("+"))
                receipt.put("phone", "+" + receipt.getString("phone"));
        }

        if (!isValid) {
            bus.send("notify-error", String.format("orderNumber: %s has no required email: %s or phone: %s",
                    receipt.getString("orderNumber"), receipt.getString("email"), receipt.getString("phone")));
        } else {
            Log.infof("receipt orderNumber: %s", receipt.getString("orderNumber"));
            client.post("/api/receipts").expect(predicate).putHeader("Authorization", "Bearer " + token)
                    .sendJson(createReceipt(receipt)).subscribe().with(response -> {
                        JsonObject operation = response.bodyAsJsonObject();
                        Log.infof("receipt %s orderNumber: %s, operation: %s",
                                operation.getString("status").toLowerCase(), receipt.getString("orderNumber"),
                                operation.getString("id"));
                        audit.setOperation(operation);
                    }, err -> {
                        bus.send("notify-error", String.format("receipt orderNumber: %s - %s",
                                receipt.getString("orderNumber"), err.getMessage()));
                    });
        }
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public void webhook(JsonObject message) {
        JsonObject data = message.getJsonObject("data");

        if (message.getString("type").equalsIgnoreCase("OPERATION")) {
            AuditRecord order = audit.findById(data.getString("externalId")).await().indefinitely();
            if (order == null) {
                Log.warnf("??? not found order by externalId: %s", data.encodePrettily());
                return;
            }

            if (data.getString("status").equalsIgnoreCase("ERROR")) {
                bus.send("notify-error", String.format("receipt orderNumber: %s - %s", order.orderNumber,
                        data.getJsonObject("data").getJsonObject("error").getString("message")));

            } else {
                Log.infof("receipt %s orderNumber: %s, operation: %s",
                        data.getString("status").toLowerCase(),
                        order.orderNumber, data.getString("id"));
            }
            audit.processOperation(data);

        } else if (message.getString("type").equalsIgnoreCase("RECEIPT")) {
            Log.infof("receipt ofd shift: %d, doc: %s", data.getLong("shiftId"),
                    data.getString("fiscalDocumentNumber"));
        }
    }

    private JsonObject createReceipt(JsonObject receipt) {
        // calc service price
        long price = (long) (receipt.getDouble("amount") * 100);
        // return receipt object
        return new JsonObject().put("externalId", receipt.getString("mdOrder")).put("deviceId", deviceId)
                .put("type", "SALE").put("timeout", 5).put("taxMode", "SIMPLE_WO")
                .put("positions",
                        new JsonArray().add(new JsonObject().put("name", "Оплата услуг").put("type", "SERVICE")
                                .put("quantity", 1).put("price", price).put("priceSum", price).put("tax", "NDS_NO_TAX")
                                .put("taxSum", 0)))
                .put("payments", new JsonArray().add(new JsonObject().put("sum", price).put("type", "CASHLESS")))
                .put("attributes", new JsonObject().put("email", receipt.getString("email")).put("phone",
                        receipt.getString("phone")))
                .put("total", new JsonObject().put("priceSum", price));
    }

    @GET
    @Path("operation/{id}")
    public Uni<JsonObject> getOperation(@PathParam("id") String operId) {
        return client.get("/api/operations/" + operId).putHeader("Authorization", "Bearer " + token).send()
                .onItem().transform(HttpResponse::bodyAsJsonObject);
    }
}
