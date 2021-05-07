package ru.openfs.sber;

import java.net.URI;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.ext.web.client.WebClient;
import ru.openfs.lbsoap.LbSoapService;

@Path("/pay")
public class SberResource {
    private static final Logger LOG = LoggerFactory.getLogger(SberResource.class);
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

    @Inject
    LbSoapService lbsoap;

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
        if (account.matches(accountPattern)) {
            String sessionId = lbsoap.login();
            try {
                if (sessionId != null && lbsoap.isActiveAgreement(sessionId, account))
                    return Response.noContent().build();
            } catch (RuntimeException e) {
                LOG.error("!!! check account: {}", e.getMessage());
                return Response.status(Status.BAD_REQUEST).build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }
        return Response.status(Status.BAD_REQUEST).build();
    }

    @POST
    @Path("checkout")
    public Uni<Response> checkout(@FormParam("uid") String account, @FormParam("amount") double amount) {
        if (account.matches(accountPattern) && amount > 10 && amount < 20000) {

            // connect billing
            String sessionId = lbsoap.login();
            if (sessionId == null) {
                return Uni.createFrom().item(Response.status(Status.BAD_REQUEST).build());
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
                            LOG.info("<-- success checkout orderNumber: {}, account: {}, amount: {}, mdOrder: {}",
                                    orderNumber, account, amount, item.getString("orderId"));
                            return Response.seeOther(URI.create(item.getString("formUrl"))).build();
                        }
                        if (item.containsKey("errorCode")) {
                            LOG.error("!!! orderNumber: {} checkout: {}", orderNumber, item.getString("errorMessage"));
                        }
                        return Response.status(Status.BAD_REQUEST).build();
                    });
                }
            } catch (RuntimeException e) {
                LOG.error("!!! checkout: {}", e.getMessage());
                return Uni.createFrom().item(Response.status(Status.BAD_REQUEST).build());
            } finally {
                lbsoap.logout(sessionId);
            }
        }
        return Uni.createFrom().item(Response.status(Status.BAD_REQUEST).build());
    }

    @GET
    @Path("sber/callback")
    public Response callback(@QueryParam("mdOrder") String mdOrder, @QueryParam("orderNumber") Long orderNumber,
            @QueryParam("operation") String operation, @QueryParam("status") int status) {

        String sessionId = lbsoap.login();
        if (sessionId == null)
            return Response.serverError().build();

        boolean isSuccess = status == 1;

        // process payment
        if (isSuccess && operation.equalsIgnoreCase("deposited")) {
            LOG.info("--> deposited orderNumber: {}", orderNumber);
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
                                    LOG.info("<-- success deposited orderNumber: {}, account: {}, amount: {}",
                                            orderNumber, agrm.getNumber(), order.getAmount());
                                    // build message
                                    JsonObject message = new JsonObject()
                                            .put("order",
                                                    new JsonObject().put("amount", order.getAmount())
                                                            .put("orderNumber", String.valueOf(orderNumber))
                                                            .put("account", agrm.getNumber()).put("mdOrder", mdOrder)
                                                            .put("email", acct.getAccount().getEmail())
                                                            .put("phone", acct.getAccount().getMobile()))
                                            .put("account", JsonObject.mapFrom(acct));
                                    // notify receipt service
                                    bus.sendAndForget("receipt-sale", message);
                                });
                    });
                });
                return Response.ok().build();
            } catch (RuntimeException e) {
                LOG.error("!!! orderNumber: {} deposited: {}", orderNumber, e.getMessage());
                return Response.serverError().build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }

        // process refund payment
        if (isSuccess && operation.equalsIgnoreCase("refunded")) {
            LOG.warn("--> refunded orderNumber: {}", orderNumber);
            return Response.ok().build();
        }

        if (isSuccess && operation.equalsIgnoreCase("approved")) {
            LOG.info("--> approved orderNumber: {} -- NOOP", orderNumber);
            return Response.ok().build();
        }

        // process unsuccess payment
        if (!isSuccess && operation.equalsIgnoreCase("deposited")) {
            LOG.info("--> unsuccess deposited orderNumber: {}", orderNumber);
            LOG.info("<-- orderNumber: {} waiting for success", orderNumber);
            return Response.ok().build();
        }

        // process decline payment
        if (!isSuccess) {
            LOG.info("--> declined orderNumber: {} ({})", orderNumber, operation);
            try {
                lbsoap.findOrderNumber(sessionId, orderNumber).ifPresent(order -> {
                    if (order.getStatus() != 0)
                        throw new RuntimeException("order was declined at " + order.getCanceldate());
                    lbsoap.cancelPrePayment(sessionId, orderNumber);
                    LOG.info("<-- success declined orderNumber: {}", orderNumber);
                    bus.sendAndForget("sber-payment-info", orderNumber);
                });
                return Response.ok().build();
            } catch (RuntimeException e) {
                LOG.error("!!! orderNumber: {} declined: {}", orderNumber, e.getMessage());
                return Response.serverError().build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }

        LOG.error("!!! unknown request");
        return Response.serverError().build();
    }

    private Uni<JsonObject> registerPayment(long orderNumber, String account, double amount) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("userName", userName);
        form.set("password", userPass);
        form.set("orderNumber", String.valueOf(orderNumber));
        form.set("amount", String.valueOf((long) (amount * 100)));
        form.set("returnUrl", successUrl);
        form.set("failUrl", failUrl);
        form.set("description", account);
        form.set("currency", "643");
        form.set("language", "ru");
        form.set("pageView", "DESKTOP");
        form.set("sessionTimeoutSecs", "300");
        return client.post("/payment/rest/register.do").sendForm(form).onItem().transform(response -> {
            return response.bodyAsJsonObject();
        });
    }

    @ConsumeEvent("sber-payment-info")
    void getSberPaymentInfo(long orderNumber) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("userName", userName);
        form.set("password", userPass);
        form.set("orderNumber", String.valueOf(orderNumber));
        client.post("/payment/rest/getOrderStatusExtended.do").sendForm(form).subscribe().with(response -> {
            JsonObject json = response.bodyAsJsonObject();
            if (!json.getString("errorCode").equalsIgnoreCase("0")) {
                LOG.warn("!!! orderNumber: {} {}", orderNumber, json.getString("errorMessage"));
            } else {
                LOG.info("--> orderNumber: {}, account: {}, amount: {}, reason: {} ({})", orderNumber,
                        json.getString("orderDescription"), json.getLong("amount") / 100,
                        json.getString("actionCodeDescription"), json.getLong("actionCode"));
            }
        });
    }

}
