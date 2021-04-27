package ru.openfs.sberonline;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api3.ExternCheckPayment;
import api3.ExternCheckPaymentResponse;
import api3.ExternPayment;
import api3.GetAccount;
import api3.GetAccountResponse;
import api3.GetExternAccount;
import api3.GetExternAccountResponse;
import api3.GetRecommendedPayment;
import api3.SoapPayment;
import api3.SoapPaymentFull;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import ru.openfs.lbsoap.LbSoapService;
import ru.openfs.sberonline.model.SberOnlineCode;
import ru.openfs.sberonline.model.SberOnlineMessage;

@Path("/pay/sber/online")
public class SberOnlineResource {
    private static final Logger LOG = LoggerFactory.getLogger(SberOnlineResource.class);
    private static final DateTimeFormatter PAY_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH:mm:ss");
    private static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Inject
    EventBus bus;

    @Inject
    LbSoapService lbsoap;

    @GET
    @Produces(MediaType.APPLICATION_XML)
    public SberOnlineMessage process(@QueryParam("ACTION") String action, @QueryParam("ACCOUNT") String account,
            @QueryParam("AMOUNT") double amount, @QueryParam("PAY_ID") String pay_id,
            @QueryParam("PAY_DATE") String pay_date) {

        // validate account format
        if (!account.matches("\\d+$")) {
            LOG.warn("<-- wrong format account: {}", account);
            return new SberOnlineMessage(SberOnlineCode.ACCOUNT_WRONG_FORMAT);
        }

        // connect to billing
        String sessionId = lbsoap.login();
        if (sessionId == null)
            return new SberOnlineMessage(SberOnlineCode.TMP_ERR);

        if (action.equalsIgnoreCase("check")) {
            LOG.info("--> check account: {}", account);
            return processCheckAccount(sessionId, account);
        } else if (action.equalsIgnoreCase("payment")) {
            LOG.info("--> payment orderNumber: {}, account: {}, amount: {}", pay_id, account, amount);
            return processPayment(sessionId, account, amount, pay_id, pay_date);
        } else
            return new SberOnlineMessage(SberOnlineCode.WRONG_ACTION);
    }

    private SberOnlineMessage processCheckAccount(String sessionId, String account) {
        GetExternAccount acctRequest = new GetExternAccount();
        acctRequest.setId(5);
        acctRequest.setStr(account);
        try {
            GetExternAccountResponse acctInfo = lbsoap.callService(acctRequest, sessionId).getJsonObject("data")
                    .mapTo(GetExternAccountResponse.class);
            if (acctInfo.getRet().get(0).getAgreements().stream().filter(ag -> ag.getNumber().equalsIgnoreCase(account))
                    .filter(ag -> ag.getClosedon().isBlank()).findAny().isPresent()) {

                // fill response
                SberOnlineMessage answer = new SberOnlineMessage(SberOnlineCode.OK);
                if (!acctInfo.getRet().get(0).getAddresses().isEmpty())
                    answer.ADDRESS = acctInfo.getRet().get(0).getAddresses().get(0).getAddress();
                answer.BALANCE = acctInfo.getRet().get(0).getAgreements().get(0).getBalance();
                // get recomended payment
                GetRecommendedPayment recPaymentReq = new GetRecommendedPayment();
                recPaymentReq.setId(acctInfo.getRet().get(0).getAgreements().get(0).getAgrmid());
                answer.REC_SUM = lbsoap.callService(recPaymentReq, sessionId).getJsonObject("data").getDouble("ret");

                // return response
                LOG.info("<-- success check account: {}", account);
                return answer;
            } else {
                LOG.warn("<-- inactive account: {}", account);
                return new SberOnlineMessage(SberOnlineCode.ACCOUNT_INACTIVE);
            }
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                LOG.warn("<-- not found account: {}", account);
                return new SberOnlineMessage(SberOnlineCode.ACCOUNT_NOT_FOUND);
            } else {
                e.printStackTrace();
                return new SberOnlineMessage(SberOnlineCode.TMP_ERR);
            }
        } finally {
            lbsoap.logout(sessionId);
        }
    }

    private SberOnlineMessage processPayment(String sessionId, String account, Double amount, String pay_id,
            String pay_date) {

        // parse payDate
        String payDateTime;
        try {
            payDateTime = LocalDateTime.parse(pay_date, PAY_DATE_FMT).format(BILL_DATE_FMT);
        } catch (DateTimeException ex) {
            LOG.warn("<-- payment orderNumber: {} wrong format pay_date: {}", pay_id, ex.getMessage());
            return new SberOnlineMessage(SberOnlineCode.WRONG_FORMAT_DATE);
        }

        // validate amount
        if (amount <= 0) {
            LOG.warn("<-- payment orderNumber: {} too small amount: {}", pay_id, amount);
            return new SberOnlineMessage(SberOnlineCode.PAY_AMOUNT_TOO_SMALL);
        }

        try {
            // do payment
            SberOnlineMessage answer = new SberOnlineMessage(SberOnlineCode.OK);
            answer.EXT_ID = lbsoap.callService(paymentRequest(pay_id, account, amount, payDateTime), sessionId)
                    .getJsonObject("data").getLong("ret");
            answer.SUM = amount;
            findPayment(sessionId, pay_id).ifPresent(p -> {
                answer.REG_DATE = toRegDate(p.getPay().getLocaldate());
                registerReceipt(sessionId, p.getUid(), amount, pay_id, account);
            });

            // return response
            LOG.info("<-- success payment orderNumber: {}, account: {}, amount: {}", pay_id, account, amount);
            return answer;

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                LOG.warn("<-- payment orderNumber: {} not found account: {}", pay_id, account);
                return new SberOnlineMessage(SberOnlineCode.ACCOUNT_NOT_FOUND);
            } else if (e.getMessage().contains("already exists")) {
                LOG.warn("<-- payment orderNumber: {} has already processed", pay_id);
                SberOnlineMessage answer = new SberOnlineMessage(SberOnlineCode.PAY_TRX_DUPLICATE);
                findPayment(sessionId, pay_id).ifPresent(p -> {
                    answer.AMOUNT = p.getAmountcurr();
                    answer.REG_DATE = toRegDate(p.getPay().getLocaldate());
                    answer.EXT_ID = p.getPay().getRecordid();
                });
                return answer;
            } else {
                e.printStackTrace();
                return new SberOnlineMessage(SberOnlineCode.TMP_ERR);
            }
        } finally {
            lbsoap.logout(sessionId);
        }
    }

    private Optional<SoapPaymentFull> findPayment(String sessionId, String pay_id) {
        try {
            ExternCheckPayment checkReq = new ExternCheckPayment();
            checkReq.setReceipt(pay_id);
            return lbsoap.callService(checkReq, sessionId).getJsonObject("data").mapTo(ExternCheckPaymentResponse.class)
                    .getRet().stream().findFirst();
        } catch (RuntimeException e) {
            LOG.error("!!! find payment orderNumber: {} return error: {}", pay_id, e.getMessage());
            return Optional.empty();
        }
    }

    private ExternPayment paymentRequest(String pay_id, String account, Double amount, String payDateTime)
            throws RuntimeException {
        SoapPayment payment = new SoapPayment();
        payment.setPaydate(payDateTime);
        payment.setAmount(amount);
        payment.setComment("SberOnline");
        payment.setModperson(0L);
        payment.setCurrid(0L);
        payment.setReceipt(pay_id);
        payment.setClassid(0L);
        ExternPayment paymentReq = new ExternPayment();
        paymentReq.setId(5);
        paymentReq.setStr(account);
        paymentReq.setVal(payment);
        paymentReq.setOperid(0L);
        paymentReq.setNotexists(1L);
        return paymentReq;
    }

    private void registerReceipt(String sessionId, long uid, double amount, String pay_id, String account)
            throws RuntimeException {
        GetAccount acctReq = new GetAccount();
        acctReq.setId(uid);
        GetAccountResponse acct = lbsoap.callService(acctReq, sessionId).getJsonObject("data")
                .mapTo(GetAccountResponse.class);
        bus.sendAndForget("register-sale",
                new JsonObject().put("amount", amount).put("orderNumber", pay_id).put("account", account)
                        .put("mdOrder", UUID.randomUUID().toString())
                        .put("email", acct.getRet().get(0).getAccount().getEmail())
                        .put("phone", acct.getRet().get(0).getAccount().getMobile()));
    }

    private static String toRegDate(String dateTime) {
        return LocalDateTime.parse(dateTime, BILL_DATE_FMT).format(PAY_DATE_FMT);
    }

}