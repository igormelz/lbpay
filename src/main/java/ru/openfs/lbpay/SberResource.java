/*
 * Copyright [2021] [OpenFS.RU]
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

import java.net.URI;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

@Path("/pay")
public class SberResource {

    private WebClient client;

    @ConfigProperty(name = "sber.host", defaultValue = "3dsec.sberbank.ru")
    String host;

    @ConfigProperty(name = "sber.user", defaultValue = "user-api")
    String userName;

    @ConfigProperty(name = "sber.pass", defaultValue = "user-pass")
    String userPass;

    @ConfigProperty(name = "sber.success.url", defaultValue = "ok")
    String successUrl;

    @ConfigProperty(name = "sber.fail.url", defaultValue = "err")
    String failUrl;

    @ConfigProperty(name = "account.pattern", defaultValue = "^\\d{6,7}$")
    String accountPattern;

    @ConfigProperty(name = "amount.min", defaultValue = "10")
    int amountMin;

    @ConfigProperty(name = "amount.max", defaultValue = "20000")
    int amountMax;

    @Inject
    LbSoapService lbsoap;

    @Inject
    AuditRepository audit;

    @Inject
    EventBus bus;

    @Inject
    Vertx vertx;

    @PostConstruct
    void initialize() {
        this.client = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost(host).setDefaultPort(443).setSsl(true));
    }

    @GET
    @Path("checkout")
    public Response checkAccount(@QueryParam("uid") String account) {
        if (account != null && account.matches(accountPattern)) {
            String sessionId = lbsoap.login();
            try {
                if (sessionId != null && lbsoap.isActiveAgreement(sessionId, account))
                    return Response.noContent().build();
            } catch (RuntimeException e) {
                Log.error("check account:", e);
                return Response.status(Status.BAD_REQUEST).build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }
        return Response.status(Status.BAD_REQUEST).build();
    }

    @POST
    @Path("checkout")
    public Response checkout(@FormParam("uid") String account, @FormParam("amount") double amount) {
        if (account.matches(accountPattern) && amount >= amountMin && amount <= amountMax) {
            
            // connect billing
            String sessionId = lbsoap.login();
            if (sessionId == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            try {
                // get active agreement id
                long agrmId = lbsoap.getAgreementId(sessionId, account);
                if (agrmId != 0) {
                    // create prepayment ordernumber
                    long orderNumber = lbsoap.createOrderNumber(sessionId, agrmId, amount);

                    // call sberbank
                    return registerPayment(orderNumber, account, amount).onItem().transform(item -> {
                        if (item.containsKey("formUrl") && item.containsKey("orderId")) {
                            Log.info(String.format(
                                    "<-- success checkout orderNumber: %d, account: %s, amount: %.2f, mdOrder: %s",
                                    orderNumber, account, amount, item.getString("orderId")));
                            return Response.seeOther(URI.create(item.getString("formUrl"))).build();
                        }
                        if (item.containsKey("errorCode")) {
                            Log.error(String.format("orderNumber: %d checkout: %s", orderNumber,
                                    item.getString("errorMessage")));
                            bus.send("notify-bot", new JsonObject().put("errorCode", 102).put("errorMessage",
                                    item.getString("errorMessage")));
                        }
                        return Response.status(Status.BAD_REQUEST).build();
                    }).await().indefinitely();
                }
            } catch (RuntimeException e) {
                Log.error("checkout:", e);
                return Response.status(Status.BAD_REQUEST).build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }
        return Response.status(Status.BAD_REQUEST).build();
    }

    @GET
    @Path("sber/callback")
    // @Transactional
    public Response callback(@QueryParam("mdOrder") String mdOrder, @QueryParam("orderNumber") Long orderNumber,
            @QueryParam("operation") String operation, @QueryParam("status") int status) {

        String sessionId = lbsoap.login();
        if (sessionId == null)
            return Response.serverError().build();

        boolean isSuccess = status == 1;

        // process payment
        if (isSuccess && operation.equalsIgnoreCase("deposited")) {
            Log.info(String.format("--> deposited orderNumber: %d", orderNumber));
            try {
                lbsoap.findOrderNumber(sessionId, orderNumber).ifPresent(order -> {

                    if (order.getStatus() != 0)
                        throw new RuntimeException("order was deposited at " + order.getPaydate());

                    // process payment
                    lbsoap.confirmPrePayment(sessionId, orderNumber, order.getAmount(), mdOrder);

                    // find account
                    lbsoap.findAccountByAgrmId(sessionId, order.getAgrmid()).ifPresent(acct -> {
                        // get agreement
                        acct.getAgreements().stream().filter(a -> a.getAgrmid() == order.getAgrmid()).findFirst()
                                .ifPresent(agrm -> {
                                    Log.info(String.format(
                                            "<-- success deposited orderNumber: %d, account: %s, amount: %.2f",
                                            orderNumber, agrm.getNumber(), order.getAmount()));
                                    // build message
                                    JsonObject receipt = new JsonObject().put("amount", order.getAmount())
                                            .put("orderNumber", String.valueOf(orderNumber))
                                            .put("account", agrm.getNumber()).put("mdOrder", mdOrder)
                                            .put("email", acct.getAccount().getEmail())
                                            .put("phone", acct.getAccount().getMobile());
                                    // make checkpoint
                                    audit.setOrder(receipt);
                                    // notify ofd
                                    bus.send("receipt-sale", receipt);
                                });
                    });
                });
                return Response.ok().build();
            } catch (RuntimeException e) {
                Log.error(String.format("!!! orderNumber: %d deposited: %s", orderNumber, e.getMessage()));
                bus.send("notify-bot", new JsonObject().put("errorCode", 103).put("errorMessage", e.getMessage()));
                return Response.serverError().build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }

        // process refund payment
        if (isSuccess && operation.equalsIgnoreCase("refunded")) {
            Log.warn(String.format("--> refunded orderNumber: %d", orderNumber));
            return Response.ok().build();
        }

        if (isSuccess && operation.equalsIgnoreCase("approved")) {
            Log.info(String.format("--> approved orderNumber: %d -- NOOP", orderNumber));
            return Response.ok().build();
        }

        // process unsuccess payment
        if (!isSuccess && operation.equalsIgnoreCase("deposited")) {
            Log.warn(String.format("--> unsuccess deposited orderNumber: %d", orderNumber));
            audit.setWaitOrder(orderNumber);
            Log.info(String.format("<-- orderNumber: %d waiting for success", orderNumber));
            return Response.ok().build();
        }

        // process decline payment
        if (operation.equalsIgnoreCase("declinedByTimeout")) {
            Log.info(String.format("--> declined orderNumber: %d (%s)", orderNumber, operation));
            try {
                lbsoap.findOrderNumber(sessionId, orderNumber).ifPresent(order -> {
                    if (order.getStatus() != 0)
                        throw new RuntimeException("order was declined at " + order.getCanceldate());
                    lbsoap.cancelPrePayment(sessionId, orderNumber);
                    Log.info(String.format("<-- success cancel orderNumber: %d", orderNumber));
                });
                return Response.ok().build();
            } catch (RuntimeException e) {
                Log.error(String.format("orderNumber: %d declined: %s", orderNumber, e.getMessage()));
                bus.send("notify-bot", new JsonObject().put("error", e.getMessage()));
                return Response.serverError().build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }

        Log.error("unknown request");
        return Response.serverError().build();
    }

    private Uni<JsonObject> registerPayment(long orderNumber, String account, double amount) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("userName", userName);
        form.set("password", userPass);
        form.set("orderNumber", Long.toString(orderNumber));
        form.set("amount", Long.toString((long) (amount * 100)));
        form.set("returnUrl", successUrl);
        form.set("failUrl", failUrl);
        form.set("description", account);
        form.set("currency", "643");
        form.set("language", "ru");
        form.set("pageView", "DESKTOP");
        form.set("sessionTimeoutSecs", "300");
        return client.post("/payment/rest/register.do").sendForm(form)
                .onItem().transform(HttpResponse::bodyAsJsonObject);
    }

    @GET
    @Path("sber/status/{orderNumber}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<JsonObject> getSberOrderStatus(@PathParam("orderNumber") long orderNumber) {
        return client.post("/payment/rest/getOrderStatusExtended.do")
                .sendForm(MultiMap.caseInsensitiveMultiMap()
                        .set("userName", userName)
                        .set("password", userPass)
                        .set("orderNumber", String.valueOf(orderNumber)))
                .onItem().transform(HttpResponse::bodyAsJsonObject);
    }

}
