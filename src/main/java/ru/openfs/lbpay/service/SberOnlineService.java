/*
 * Copyright 2021-2024 OpenFS.RU
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
package ru.openfs.lbpay.service;

import static ru.openfs.lbpay.model.sberonline.SberOnlineResponseType.*;

import java.util.UUID;

import api3.SoapAccountFull;
import api3.SoapPaymentFull;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;
import ru.openfs.lbpay.exception.SberOnlineException;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.sberonline.SberOnlineResponse;

@ApplicationScoped
public class SberOnlineService {

    @Inject
    LbCoreSoapClient lbCoreSoapClient;

    @Inject
    EventBus eventBus;

    /**
     * process SberOnline check account
     * 
     * @param  account
     * @return
     */
    public SberOnlineResponse processCheckAccount(String account) {
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {

            var acctInfo = adapter.findAccountInfo(account)
                    .orElseThrow(() -> new SberOnlineException(
                            ACCOUNT_NOT_FOUND, String.format("check account:[%s] not found", account)));

            return acctInfo.getAgreements().stream()
                    .filter(agrm -> agrm.getNumber().equalsIgnoreCase(account))
                    .filter(agrm -> agrm.getClosedon().isBlank())
                    .findFirst()
                    .map(agrm -> responseCheck(agrm.getBalance(), adapter.getRecomendedPayment(agrm.getAgrmid()), acctInfo))
                    .orElseThrow(() -> new SberOnlineException(
                            ACCOUNT_INACTIVE, String.format("check account:[%s] inactive", account)));

        } catch (SberOnlineException soe) {
            throw soe;
        } catch (Exception e) {
            throw new SberOnlineException(TMP_ERR, e.getMessage());
        }
    }

    /**
     * process sber online payment
     *  
     * @param account
     * @param payId
     * @param amount
     * @param payDate
     * @return
     */
    public SberOnlineResponse processPayment(String account, String payId, Double amount, String payDate) {
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {

            // validate account
            var acctInfo = adapter.findAccountInfo(account)
                    .orElseThrow(() -> new SberOnlineException(
                            ACCOUNT_NOT_FOUND, String.format("paid account:[%s] not found", account)));

            var existPayment = adapter.findPayment(payId);
            if (existPayment.isPresent()) {
                Log.warnf("duplicate payment: [%s]", payId);
                return responseDuplicate(existPayment.get());
            }

            // do payment
            var paymentId = adapter.sberOnlinePayment(payId, account, amount, payDate);

            // get payment info
            var payment = adapter.findPayment(payId)
                    .orElseThrow(() -> new SberOnlineException(TMP_ERR, "payment not processed"));

            // invoke receipt 
            registerReceipt(amount, payId, account, acctInfo);

            Log.infof("Processed payment orderNumber:[%s], account: %s, amount:%.2f",
                    payId, account, amount);

            return responsePayment(paymentId, amount, payment.getPay().getLocaldate());

        } catch (Exception e) {
            eventBus.send("notify-error",
                    String.format("payment orderNumber: %s - %s", payId, e.getMessage()));
            throw new SberOnlineException(TMP_ERR, e.getMessage());
        }
    }

    private SberOnlineResponse responseCheck(double balance, double recSum, SoapAccountFull acctInfo) {
        return new SberOnlineResponse.Builder()
                .responseType(OK)
                .setBalance(balance)
                .setRecSum(recSum)
                .setAddress(acctInfo.getAddresses().isEmpty() ? null : acctInfo.getAddresses().get(0).getAddress())
                .build();
    }

    private SberOnlineResponse responseDuplicate(SoapPaymentFull existingPayment) {
        return new SberOnlineResponse.Builder()
                .responseType(PAY_TRX_DUPLICATE)
                .setExtId(existingPayment.getPay().getRecordid())
                .setRegDate(existingPayment.getPay().getLocaldate())
                .setAmount(existingPayment.getAmountcurr())
                .build();
    }

    private SberOnlineResponse responsePayment(long paymentId, double amount, String regDate) {
        return new SberOnlineResponse.Builder()
                .responseType(OK)
                .setExtId(paymentId)
                .setSum(amount)
                .setRegDate(regDate)
                .build();
    }

    private void registerReceipt(double amount, String orderNumber, String account, SoapAccountFull acctInfo) {
        eventBus.send("register-receipt", new ReceiptOrder(
                amount, orderNumber, account, UUID.randomUUID().toString(),
                new ReceiptCustomer(
                        acctInfo.getAccount().getEmail(),
                        acctInfo.getAccount().getMobile())));
    }
}
