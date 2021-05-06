package ru.openfs.sberonline;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        if (sessionId == null) {
            return new SberOnlineMessage(SberOnlineCode.TMP_ERR);
        }

        if (action.equalsIgnoreCase("check")) {

            // process check acount
            LOG.info("--> check account: {}", account);
            return processCheckAccount(sessionId, account);

        } else if (action.equalsIgnoreCase("payment")) {

            // process payment
            LOG.info("--> payment orderNumber: {}, account: {}, amount: {}", pay_id, account, amount);
            return processPayment(sessionId, account, amount, pay_id, pay_date);

        } else {
            // raise error
            return new SberOnlineMessage(SberOnlineCode.WRONG_ACTION);
        }
    }

    private SberOnlineMessage processCheckAccount(String sessionId, String account) {
        try {

            SberOnlineMessage answer = new SberOnlineMessage();

            // find account by agreement number
            // when not found will throw exception
            lbsoap.findAccountByAgrmNum(sessionId, account).ifPresent(acctInfo -> {

                // get active agreement
                acctInfo.getAgreements().stream().filter(agrm -> agrm.getNumber().equalsIgnoreCase(account))
                        .filter(agrm -> agrm.getClosedon().isBlank()).findFirst().ifPresentOrElse(agrm -> {

                            // add address
                            if (!acctInfo.getAddresses().isEmpty()) {
                                answer.ADDRESS = acctInfo.getAddresses().get(0).getAddress();
                            }
                            // add current balance
                            answer.BALANCE = agrm.getBalance();

                            // ask recomended payment
                            answer.REC_SUM = lbsoap.getRecomendedPayment(sessionId, agrm.getAgrmid());

                            // success response
                            answer.setResponse(SberOnlineCode.OK);
                            LOG.info("<-- success check account: {}", account);

                        }, () -> {
                            // process inactive agreement response
                            answer.setResponse(SberOnlineCode.ACCOUNT_INACTIVE);
                            LOG.warn("<-- check account: {} inactive", account);
                        });
            });

            // return response
            return answer;

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                // process if not found account
                LOG.warn("<-! check account: {} not found", account);
                return new SberOnlineMessage(SberOnlineCode.ACCOUNT_NOT_FOUND);
            } else {
                // common error
                LOG.error("!!! check account: {} - {}", account, e.getMessage());
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
            answer.EXT_ID = lbsoap.sberOnlinePayment(sessionId, pay_id, account, amount, payDateTime);
            answer.SUM = amount;
            lbsoap.findPayment(sessionId, pay_id).ifPresent(payment -> {
                answer.REG_DATE = toRegDate(payment.getPay().getLocaldate());
                lbsoap.findAccountByAgrmNum(sessionId, account).ifPresent(acct -> {
                    // build message
                    JsonObject message = new JsonObject()
                            .put("order",
                                    new JsonObject().put("amount", amount).put("orderNumber", pay_id)
                                            .put("account", account).put("mdOrder", UUID.randomUUID().toString())
                                            .put("email", acct.getAccount().getEmail())
                                            .put("phone", acct.getAccount().getMobile()))
                            .put("account", JsonObject.mapFrom(acct));
                    // notify receipt service
                    bus.sendAndForget("receipt-sale", message);
                });
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
                lbsoap.findPayment(sessionId, pay_id).ifPresent(p -> {
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

    private static String toRegDate(String dateTime) {
        return LocalDateTime.parse(dateTime, BILL_DATE_FMT).format(PAY_DATE_FMT);
    }

}