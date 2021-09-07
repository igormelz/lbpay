package ru.openfs.lbpay;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Multi;
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
import ru.openfs.lbpay.audit.AuditRecord;
import ru.openfs.lbpay.audit.AuditRepository;

@Path("/pay/dreamkas")
public class DreamkasResource {
    private static final Logger LOG = LoggerFactory.getLogger(DreamkasResource.class);
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
            LOG.error(err);
            return new RuntimeException(err);
        }
        LOG.error("!!! DK service error:{}", result.message());
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
            LOG.error("!!! receipt orderNumber: {} - no required email: {} or phone: {}",
                    receipt.getString("orderNumber"), receipt.getString("email"), receipt.getString("phone"));
            bus.send("notify-bot", receipt.put("errorMessage", "no required email or phone"));
            return;
        }

        LOG.info("<-- receipt orderNumber: {}", receipt.getString("orderNumber"));
        client.post("/api/receipts").expect(predicate).putHeader("Authorization", "Bearer " + token)
                .sendJson(createReceipt(receipt)).subscribe().with(response -> {
                    JsonObject operation = response.bodyAsJsonObject();
                    LOG.info("--> {} receipt orderNumber: {}, operation: {}",
                            operation.getString("status").toLowerCase(), receipt.getString("orderNumber"),
                            operation.getString("id"));
                    audit.setOperation(operation);
                }, err -> {
                    LOG.error("!!! receipt orderNumber: {} - {}", receipt.getString("orderNumber"), err.getMessage());
                    bus.send("notify-bot", receipt.put("errorMessage", err.getMessage()));
                });
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public void webhook(JsonObject message) {
        JsonObject data = message.getJsonObject("data");

        if (message.getString("type").equalsIgnoreCase("OPERATION")) {
            AuditRecord order = audit.findById(data.getString("externalId")).await().indefinitely();
            if (order == null) {
                LOG.error("!!! not found order by externalId: {}", data.getString("externalId"));
                return;
            }

            if (data.getString("status").equalsIgnoreCase("ERROR")) {
                LOG.error("!!! receipt orderNumber: {} - {}", order.orderNumber,
                        data.getJsonObject("data").getJsonObject("error").getString("message"));
                bus.send("notify-bot", data.put("orderNumber", order.orderNumber));
            } else {
                LOG.info("--> {} receipt orderNumber: {}, operation: {}", data.getString("status").toLowerCase(),
                        order.orderNumber, data.getString("id"));
            }
            audit.processOperation(data);

        } else if (message.getString("type").equalsIgnoreCase("RECEIPT")) {
            LOG.info("--> ofd receipt shift: {}, doc: {}",
                    data.getLong("shiftId"),
                    data.getValue("fiscalDocumentNumber", "fiscalDocumentNumber"));
            //audit.setFd(data);
        }
    }

    @GET
    @Path("order")
    public Multi<AuditRecord> getOrders() {
        return audit.findAll();
    }

    @GET
    @Path("order/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<AuditRecord> order(@PathParam("key") String key) {
        return audit.findById(key);
    }

    @PUT
    @Path("order/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiptOrder(@PathParam("key") String key) {
        AuditRecord order = audit.findById(key).await().indefinitely();
        if (order == null) {
            LOG.error("!!! re-processing order:{} not found", key);
            throw new NotFoundException("order not found");
        }
        if (order.operId == null) {
            LOG.info("--> re-processing orderNumber: {} with status: not registered", order.orderNumber);
            receiptSale(JsonObject.mapFrom(order));
            return Uni.createFrom().item("re-processing not registered order");
        } else if (order.status.equalsIgnoreCase("ERROR")) {
            LOG.warn("--> re-processing orderNumber: {} with status: error", order.orderNumber);
            receiptSale(JsonObject.mapFrom(order));
            return Uni.createFrom().item("re-processing error order");
        } else {
            LOG.info("--> ask operation status");
            return client.get("/api/operations/" + order.operId).expect(predicate)
                    .putHeader("Authorization", "Bearer " + token).send().onItem().transform(resp -> resp.bodyAsString())
                    .onFailure().recoverWithItem("DK service error");
        }
    }

    private JsonObject createReceipt(JsonObject receipt) {
        // calc service price
        long price = (long) (receipt.getDouble("amount") * 100);
        // {
        // "externalId":"18e3e142-2f88-7a6d-ad98-56c501fa9b79",
        // "deviceId":109266,
        // "type":"SALE",
        // "timeout":5,
        // "taxMode":"SIMPLE_WO",
        // "positions":[{"name":"Оплата услуг","type":"SERVICE","quantity":1,"price":55000,"priceSum":55000,"tax":"NDS_NO_TAX","taxSum":0}],
        // "payments":[{"sum":55000,"type":"CASHLESS"}],
        // "attributes":{"email":"Dmi7rich9407@gmail.com","phone":"+79650902202"},
        // "total":{"priceSum":55000}}
        return new JsonObject()
                .put("externalId", receipt.getString("mdOrder"))
                .put("deviceId", deviceId)
                .put("type", "SALE")
                .put("timeout", 5)
                .put("taxMode", "SIMPLE_WO")
                .put("positions", new JsonArray()
                        .add(new JsonObject()
                                .put("name", "Оплата услуг")
                                .put("type", "SERVICE")
                                .put("quantity", 1)
                                .put("price", price)
                                .put("priceSum", price)
                                .put("tax", "NDS_NO_TAX")
                                .put("taxSum", 0)))
                .put("payments", new JsonArray().add(new JsonObject().put("sum", price).put("type", "CASHLESS")))
                .put("attributes",
                        new JsonObject().put("email", receipt.getString("email")).put("phone", receipt.getString("phone")))
                .put("total", new JsonObject().put("priceSum", price));
    }

}
