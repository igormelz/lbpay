package ru.openfs.sber;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

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

import api3.CancelPrePayment;
import api3.ConfirmPrePayment;
import api3.GetAccount;
import api3.GetAccountResponse;
import api3.GetAgreements;
import api3.GetAgreementsBrief;
import api3.GetAgreementsBriefResponse;
import api3.GetAgreementsResponse;
import api3.GetPrePayments;
import api3.GetPrePaymentsResponse;
import api3.InsPrePayment;
import api3.SoapAgreement;
import api3.SoapAgreementBrief;
import api3.SoapFilter;
import api3.SoapPrePayment;
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
    private static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
    public Response validateAccount(@QueryParam("uid") String account) {
        if (account.matches(accountPattern)) {
            String sessionId = lbsoap.login();
            try {
                if (sessionId != null && isActiveAgreement(sessionId, account))
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
            String sessionId = lbsoap.login();
            if (sessionId == null)
                return Uni.createFrom().item(Response.status(Status.BAD_REQUEST).build());
            try {
                Optional<SoapAgreementBrief> agrm = findAgreementByNumber(sessionId, account);
                if (agrm.isPresent() && agrm.get().getClosedon().isBlank()) {
                    LOG.info("--> checkout account: {}, amount: {}", account, amount);
                    long orderNumber = createOrderNumber(sessionId, agrm.get().getAgrmid(), amount);
                    LOG.info("<-- register orderNumber: {}, account: {}, amount: {}", orderNumber, account, amount);
                    return registerPayment(orderNumber, account, amount).onItem().transform(item -> {
                        if (item.containsKey("formUrl")) {
                            LOG.info("--> success registered orderNumber: {} with id: {}", orderNumber,
                                    item.getString("orderId"));
                            return Response.seeOther(URI.create(item.getString("formUrl"))).build();
                        }
                        if (item.containsKey("errorCode")) {
                            LOG.error("!!! register payment orderNumber: {} {}", orderNumber,
                                    item.getString("errorMessage"));
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
        if (isSuccess && operation.equalsIgnoreCase("deposited")) {
            LOG.info("--> deposited orderNumber: {}", orderNumber);
            try {
                findOrderNumber(sessionId, orderNumber).ifPresent(order -> {
                    if (order.getStatus() == 0) {
                        doConfirmPrePayment(sessionId, orderNumber, order.getAmount(), mdOrder);
                        findAgreementById(sessionId, order.getAgrmid()).ifPresent(account -> {
                            LOG.info("<-- success refill orderNumber: {}, account: {}, amount: {}", orderNumber,
                                    account.getNumber(), order.getAmount());
                            doRegisterReceipt(sessionId, account.getUid(), orderNumber, account.getNumber(),
                                    order.getAmount(), mdOrder);
                        });
                    }
                });
                return Response.ok().build();
            } catch (RuntimeException e) {
                LOG.error("!!! orderNumber: {} don't processed: {}", orderNumber, e.getMessage());
                return Response.serverError().build();
            } finally {
                lbsoap.logout(sessionId);
            }
        }

        if (isSuccess && operation.equalsIgnoreCase("refunded")) {
            LOG.warn("--> refunded orderNumber: {}", orderNumber);
            return Response.ok().build();
        }

        if (isSuccess && operation.equalsIgnoreCase("approved")) {
            LOG.info("--> approved orderNumber: {} -- NOOP", orderNumber);
            return Response.ok().build();
        }

        if (!isSuccess && operation.equalsIgnoreCase("deposited")) {
            LOG.info("--> unsuccess deposited orderNumber: {}", orderNumber);
            LOG.info("<-- orderNumber: {} waiting for success", orderNumber);
            return Response.ok().build();
        }

        if (!isSuccess) {
            LOG.info("--> declined orderNumber: {} ({})", orderNumber, operation);
            try {
                findOrderNumber(sessionId, orderNumber).ifPresent(order -> {
                    if (order.getStatus() == 0) {
                        doCancelPrePayment(sessionId, orderNumber);
                        LOG.info("<-- success canceled orderNumber: {}", orderNumber);
                        // ask and print reason
                        bus.sendAndForget("sber-payment-info", orderNumber);
                    } else {
                        LOG.warn("<-- orderNumber: {} already processed at {}", orderNumber, order.getCanceldate());
                    }
                });
                return Response.ok().build();
            } catch (RuntimeException e) {
                LOG.error("!!! orderNumber: {} don't cancel: {}", orderNumber, e.getMessage());
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

    private long createOrderNumber(String sessionId, long id, double amount) throws RuntimeException {
        SoapPrePayment data = new SoapPrePayment();
        data.setAgrmid(id);
        data.setAmount(amount);
        data.setCurname("RUR");
        data.setComment("form checkout");
        data.setPaydate(BILL_DATE_FMT.format(LocalDateTime.now()));
        InsPrePayment request = new InsPrePayment();
        request.setVal(data);
        return lbsoap.callService(request, sessionId).getJsonObject("data").getLong("ret");
    }

    private void doConfirmPrePayment(String sessionId, long orderNumber, double amount, String receipt)
            throws RuntimeException {
        ConfirmPrePayment request = new ConfirmPrePayment();
        request.setRecordid(orderNumber);
        request.setAmount(amount);
        request.setReceipt(receipt);
        request.setPaydate(BILL_DATE_FMT.format(LocalDateTime.now()));
        lbsoap.callService(request, sessionId);
    }

    private void doCancelPrePayment(String sessionId, long orderNumber) throws RuntimeException {
        CancelPrePayment request = new CancelPrePayment();
        request.setRecordid(orderNumber);
        request.setCanceldate(BILL_DATE_FMT.format(LocalDateTime.now()));
        lbsoap.callService(request, sessionId);
    }

    private Optional<SoapPrePayment> findOrderNumber(String sessionId, Long orderNumber) throws RuntimeException {
        SoapFilter filter = new SoapFilter();
        filter.setRecordid(orderNumber);
        GetPrePayments request = new GetPrePayments();
        request.setFlt(filter);
        return lbsoap.callService(request, sessionId).getJsonObject("data").mapTo(GetPrePaymentsResponse.class).getRet()
                .stream().findFirst();
    }

    private Optional<SoapAgreement> findAgreementById(String sessionId, long id) throws RuntimeException {
        SoapFilter filter = new SoapFilter();
        filter.setAgrmid(id);
        GetAgreements request = new GetAgreements();
        request.setFlt(filter);
        return lbsoap.callService(request, sessionId).getJsonObject("data").mapTo(GetAgreementsResponse.class).getRet()
                .stream().findFirst();
    }

    private boolean isActiveAgreement(String sessionId, String number) throws RuntimeException {
        var account = findAgreementByNumber(sessionId, number);
        return account.isPresent() && account.get().getClosedon().isBlank();
    }

    private Optional<SoapAgreementBrief> findAgreementByNumber(String sessionId, String number)
            throws RuntimeException {
        SoapFilter filter = new SoapFilter();
        filter.setAgrmnum(number);
        GetAgreementsBrief request = new GetAgreementsBrief();
        request.setFlt(filter);
        return lbsoap.callService(request, sessionId).getJsonObject("data").mapTo(GetAgreementsBriefResponse.class)
                .getRet().stream().filter(agrm -> agrm.getNumber().equalsIgnoreCase(number)).findFirst();
    }

    private void doRegisterReceipt(String sessionId, long uid, long orderNumber, String account, Double amount,
            String mdOrder) {
        GetAccount request = new GetAccount();
        request.setId(uid);
        GetAccountResponse acct = lbsoap.callService(request, sessionId).getJsonObject("data")
                .mapTo(GetAccountResponse.class);
        bus.sendAndForget("register-sale",
                new JsonObject().put("amount", amount).put("orderNumber", String.valueOf(orderNumber))
                        .put("account", account).put("mdOrder", mdOrder)
                        .put("email", acct.getRet().get(0).getAccount().getEmail())
                        .put("phone", acct.getRet().get(0).getAccount().getMobile()));
    }

    @ConsumeEvent("sber-payment-info")
    void getSberPaymentInfo(long orderNumber) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("userName", userName);
        form.set("password", userPass);
        form.set("orderNumber", String.valueOf(orderNumber));
        client.post("/payment/rest/getOrderStatusExtended.do").sendForm(form).subscribe().with(response -> {
            JsonObject result = response.bodyAsJsonObject();
            LOG.info("canceled orderNumber: {}, account: {}, amount: {}, reason: {} [{}]", orderNumber,
                    result.getString("orderDescription"), result.getDouble("amount") / 100,
                    result.getString("actionCodeDescription"), result.getLong("actionCode"));
        });
    }

}
