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

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import ru.openfs.lbpay.audit.AuditRepository;
import ru.openfs.lbpay.model.SberOnlineCode;
import ru.openfs.lbpay.model.SberOnlineMessage;

@Path("/pay/sber/online")
public class SberOnlineResource {
    private static final Logger LOG = LoggerFactory.getLogger(SberOnlineResource.class);
    private static final DateTimeFormatter PAY_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH:mm:ss");
    private static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    EventBus bus;

    @Inject
    LbSoapService lbsoap;

    @Inject
    AuditRepository audit;

    @Inject
    ProducerTemplate producer;

    @GET
    @Produces(MediaType.APPLICATION_XML)
    // @Transactional
    public String process(@QueryParam("ACTION") String action, @QueryParam("ACCOUNT") String account,
            @QueryParam("AMOUNT") double amount, @QueryParam("PAY_ID") String pay_id,
            @QueryParam("PAY_DATE") String pay_date) {

        // validate account format
        if (!account.matches("\\d+$")) {
            LOG.warn("<-- wrong format account: {}", account);
            return producer.requestBody("direct:marshalSberOnline",
                    new SberOnlineMessage(SberOnlineCode.ACCOUNT_WRONG_FORMAT), String.class);
        }

        // connect to billing
        String sessionId = lbsoap.login();
        if (sessionId == null) {
            // return new SberOnlineMessage(SberOnlineCode.TMP_ERR);
            return producer.requestBody("direct:marshalSberOnline", new SberOnlineMessage(SberOnlineCode.TMP_ERR),
                    String.class);
        }

        // process check acount
        if (action.equalsIgnoreCase("check")) {
            LOG.info("--> check account: {}", account);
            // return processCheckAccount(sessionId, account);
            return producer.requestBody("direct:marshalSberOnline", processCheckAccount(sessionId, account),
                    String.class);
        }

        // process payment
        if (action.equalsIgnoreCase("payment")) {
            LOG.info("--> payment orderNumber: {}, account: {}, amount: {}", pay_id, account, amount);
            // return processPayment(sessionId, account, amount, pay_id, pay_date);
            return producer.requestBody("direct:marshalSberOnline",
                    processPayment(sessionId, account, amount, pay_id, pay_date), String.class);
        }

        // raise error
        // return new SberOnlineMessage(SberOnlineCode.WRONG_ACTION);
        return producer.requestBody("direct:marshalSberOnline", new SberOnlineMessage(SberOnlineCode.WRONG_ACTION),
                String.class);
    }

    private SberOnlineMessage processCheckAccount(String sessionId, String account) {
        SberOnlineMessage answer = new SberOnlineMessage();
        try {
            // find account by agreement number when not found will throw exception
            lbsoap.findAccountByAgrmNum(sessionId, account).ifPresent(acctInfo -> {

                // get active agreement
                acctInfo.getAgreements().stream().filter(agrm -> agrm.getNumber().equalsIgnoreCase(account))
                        .filter(agrm -> agrm.getClosedon().isBlank()).findFirst().ifPresent(agrm -> {

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

                        });

                // process inactive agreement response
                if (answer.MESSAGE == null) {
                    answer.setResponse(SberOnlineCode.ACCOUNT_INACTIVE);
                    LOG.warn("<-- check account: {} inactive", account);
                }
            });

            // return response
            return answer;

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                // raise not found account
                LOG.warn("<-! check account: {} not found", account);
                return new SberOnlineMessage(SberOnlineCode.ACCOUNT_NOT_FOUND);
            } else {
                // common error
                LOG.error("!!! check account: {} {}", account, e.getMessage());
                bus.send("notify-bot", new JsonObject().put("error", e.getMessage()));
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
                    JsonObject receipt = new JsonObject().put("amount", amount).put("orderNumber", pay_id)
                            .put("account", account).put("mdOrder", UUID.randomUUID().toString())
                            .put("email", acct.getAccount().getEmail()).put("phone", acct.getAccount().getMobile());
                    // make audit check point
                    audit.setOrder(receipt);
                    // notify receipt service
                    bus.send("receipt-sale", receipt);
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
                LOG.error("!!! orderNumber:{} - {}", pay_id, e.getMessage());
                bus.send("notify-bot", new JsonObject().put("orderNumber", pay_id).put("errorMessage", e.getMessage()));
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
