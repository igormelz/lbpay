package ru.openfs.lbsoap;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api3.CancelPrePayment;
import api3.ConfirmPrePayment;
import api3.ExternCheckPayment;
import api3.ExternCheckPaymentResponse;
import api3.ExternPayment;
import api3.GetAgreementsBrief;
import api3.GetAgreementsBriefResponse;
import api3.GetExternAccount;
import api3.GetExternAccountResponse;
import api3.GetPrePayments;
import api3.GetPrePaymentsResponse;
import api3.GetRecommendedPayment;
import api3.InsPrePayment;
import api3.Login;
import api3.Logout;
import api3.SoapAccountFull;
import api3.SoapAgreementBrief;
import api3.SoapFilter;
import api3.SoapPayment;
import api3.SoapPaymentFull;
import api3.SoapPrePayment;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.ext.web.client.predicate.ErrorConverter;
import io.vertx.mutiny.ext.web.client.predicate.ResponsePredicate;

@Singleton
public class LbSoapService {
    private static final Logger LOG = LoggerFactory.getLogger(LbSoapService.class);
    public static final Long AGRM_NUM = 5L;
    public static final Long UID = 10L;
    public static final Long AGRM_ID = 11L;
    private static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private WebClient client;

    @ConfigProperty(name = "lbcore.host", defaultValue = "127.0.0.1")
    String host;

    @ConfigProperty(name = "lbcore.login")
    String login;

    @ConfigProperty(name = "lbcore.pass")
    String pass;

    @Inject
    ProducerTemplate producer;

    @Inject
    Vertx vertx;

    @PostConstruct
    void initialize() {
        this.client = WebClient.create(vertx, new WebClientOptions().setDefaultHost(host).setDefaultPort(34012));
    }

    public String login() {
        Login soapRequest = new Login();
        soapRequest.setLogin(login);
        soapRequest.setPass(pass);
        try {
            return callService(soapRequest, null).getString("sessionId");
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            return null;
        }
    }

    public void logout(String sessionId) {
        callService(new Logout(), sessionId);
    }

    public boolean isActiveAgreement(String sessionId, String number) throws RuntimeException {
        return findAgreementByNumber(sessionId, number).filter(agrm -> agrm.getClosedon().isBlank()).isPresent();
    }

    public long getAgreementId(String sessionId, String number) throws RuntimeException {
        return findAgreementByNumber(sessionId, number).filter(agrm -> agrm.getClosedon().isBlank())
                .map(agrm -> agrm.getAgrmid()).orElse(0L);
    }

    public Optional<SoapPaymentFull> findPayment(String sessionId, String pay_id) throws RuntimeException {
        ExternCheckPayment checkReq = new ExternCheckPayment();
        checkReq.setReceipt(pay_id);
        return callService(checkReq, sessionId).getJsonObject("data").mapTo(ExternCheckPaymentResponse.class).getRet()
                .stream().findFirst();
    }

    public long sberOnlinePayment(String sessionId, String receipt, String account, Double amount, String payDateTime)
            throws RuntimeException {
        SoapPayment payment = new SoapPayment();
        payment.setPaydate(payDateTime);
        payment.setAmount(amount);
        payment.setComment("SberOnline");
        payment.setModperson(0L);
        payment.setCurrid(0L);
        payment.setReceipt(receipt);
        payment.setClassid(0L);
        ExternPayment request = new ExternPayment();
        request.setId(5);
        request.setStr(account);
        request.setVal(payment);
        request.setOperid(0L);
        request.setNotexists(1L);
        return callService(request, sessionId).getJsonObject("data").getLong("ret");
    }

    // get recomended payment by agrm_id
    public double getRecomendedPayment(String sessionId, long id) throws RuntimeException {
        GetRecommendedPayment recPaymentReq = new GetRecommendedPayment();
        recPaymentReq.setId(id);
        return callService(recPaymentReq, sessionId).getJsonObject("data").getDouble("ret");
    }

    public void cancelPrePayment(String sessionId, long orderNumber) throws RuntimeException {
        CancelPrePayment request = new CancelPrePayment();
        request.setRecordid(orderNumber);
        request.setCanceldate(BILL_DATE_FMT.format(LocalDateTime.now()));
        callService(request, sessionId);
    }

    public void confirmPrePayment(String sessionId, long orderNumber, double amount, String receipt)
            throws RuntimeException {
        ConfirmPrePayment request = new ConfirmPrePayment();
        request.setRecordid(orderNumber);
        request.setAmount(amount);
        request.setReceipt(receipt);
        request.setPaydate(BILL_DATE_FMT.format(LocalDateTime.now()));
        callService(request, sessionId);
    }

    public long createOrderNumber(String sessionId, long id, double amount) throws RuntimeException {
        SoapPrePayment data = new SoapPrePayment();
        data.setAgrmid(id);
        data.setAmount(amount);
        data.setCurname("RUR");
        data.setComment("form checkout");
        InsPrePayment request = new InsPrePayment();
        request.setVal(data);
        return callService(request, sessionId).getJsonObject("data").getLong("ret");
    }

    public Optional<SoapPrePayment> findOrderNumber(String sessionId, Long orderNumber) throws RuntimeException {
        SoapFilter filter = new SoapFilter();
        filter.setRecordid(orderNumber);
        GetPrePayments request = new GetPrePayments();
        request.setFlt(filter);
        return callService(request, sessionId).getJsonObject("data").mapTo(GetPrePaymentsResponse.class).getRet()
                .stream().findFirst();
    }

    public Optional<SoapAgreementBrief> findAgreementByNumber(String sessionId, String number) throws RuntimeException {
        SoapFilter filter = new SoapFilter();
        filter.setAgrmnum(number);
        GetAgreementsBrief request = new GetAgreementsBrief();
        request.setFlt(filter);
        return callService(request, sessionId).getJsonObject("data").mapTo(GetAgreementsBriefResponse.class).getRet()
                .stream().filter(agrm -> agrm.getNumber().equalsIgnoreCase(number)).findFirst();
    }

    public Optional<SoapAccountFull> findAccountByAgrmId(String sessionId, long id) throws RuntimeException {
        GetExternAccount request = new GetExternAccount();
        request.setId(AGRM_ID);
        request.setStr(Long.toString(id));
        return callService(request, sessionId).getJsonObject("data").mapTo(GetExternAccountResponse.class).getRet()
                .stream().findFirst();
    }

    public Optional<SoapAccountFull> findAccountByAgrmNum(String sessionId, String number) throws RuntimeException {
        GetExternAccount request = new GetExternAccount();
        request.setId(AGRM_NUM);
        request.setStr(number);
        return callService(request, sessionId).getJsonObject("data").mapTo(GetExternAccountResponse.class).getRet()
                .stream().findFirst();
    }

    protected JsonObject callService(Object request, String sessionId) throws RuntimeException {
        Set<String> cookie = sessionId != null ? Collections.singleton(sessionId) : Collections.emptySet();
        return client.post("/").expect(predicate).putHeader("Cookie", cookie)
                .putHeader("Content-type", "application/xml")
                .sendBuffer(Buffer.buffer(producer.requestBody("direct:marshalSoap", request, byte[].class))).onItem()
                .transform(response -> {
                    JsonObject json = response.headers().contains("Set-Cookie")
                            ? new JsonObject().put("sessionId", parseSessionId(response.cookies()))
                            : new JsonObject();
                    json.put("data", JsonObject
                            .mapFrom(producer.requestBody("direct:unmarshalSoap", response.bodyAsBuffer().getBytes())));
                    return json;
                }).await().atMost(Duration.ofSeconds(1));
    }

    ErrorConverter converter = ErrorConverter.createFullBody(result -> {
        HttpResponse<Buffer> response = result.response();
        String err = producer.requestBody("direct:getFaultMessage", response.bodyAsString(), String.class);
        if (err != null && !err.isBlank())
            return new RuntimeException(err);
        return new RuntimeException(result.message());
    });

    ResponsePredicate predicate = ResponsePredicate.create(ResponsePredicate.SC_SUCCESS, converter);

    private String parseSessionId(List<String> cookie) {
        return cookie.stream().filter(c -> c.startsWith("sessnum") && c.contains("Max-Age")).findAny()
                .map(c -> c.substring(0, c.indexOf(";"))).orElse(null);
    }

}
