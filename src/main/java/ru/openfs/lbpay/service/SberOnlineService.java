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

import static ru.openfs.lbpay.resource.sberonline.model.SberOnlineResponseType.*;

import java.util.UUID;

import api3.SoapAccountFull;
import api3.SoapPaymentFull;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.resource.sberonline.exception.SberOnlineException;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineMessage;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineRequest;

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
    public SberOnlineMessage processCheckAccount(String account) {
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
     * process SberOnline payment
     * 
     * @param  request
     * @return
     */
    public SberOnlineMessage processPayment(SberOnlineRequest request) {
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {

            // validate account
            var acctInfo = adapter.findAccountInfo(request.account())
                    .orElseThrow(() -> new SberOnlineException(
                            ACCOUNT_NOT_FOUND, String.format("paid account:[%s] not found", request.account())));

            var existPayment = adapter.findPayment(request.payId());
            if (existPayment.isPresent()) {
                Log.warnf("duplicate payment: [%s]", request.payId());
                return responseDuplicate(existPayment.get());
            }

            // do payment
            var paymentId = adapter.sberOnlinePayment(
                    request.payId(), request.account(), request.amount(), request.payDate());

            // get payment info
            var payment = adapter.findPayment(request.payId())
                    .orElseThrow(() -> new SberOnlineException(TMP_ERR, "payment not processed"));

            // invoke receipt 
            createReceiptOrder(request.amount(), request.payId(), request.account(), acctInfo);

            Log.infof("Processed payment orderNumber:[%s], account: %s, amount:%.2f",
                    request.payId(), request.account(), request.amount());

            return responsePayment(paymentId, request.amount(), payment.getPay().getLocaldate());

        } catch (Exception e) {
            eventBus.send("notify-error",
                    String.format("payment orderNumber: %s - %s", request.payId(), e.getMessage()));
            throw new SberOnlineException(TMP_ERR, e.getMessage());
        }
    }

    private SberOnlineMessage responseCheck(double balance, double recSum, SoapAccountFull acctInfo) {
        return new SberOnlineMessage.Builder()
                .responseType(OK)
                .setBalance(balance)
                .setRecSum(recSum)
                .setAddress(acctInfo.getAddresses().isEmpty() ? null : acctInfo.getAddresses().get(0).getAddress())
                .build();
    }

    private SberOnlineMessage responseDuplicate(SoapPaymentFull existingPayment) {
        return new SberOnlineMessage.Builder()
                .responseType(PAY_TRX_DUPLICATE)
                .setExtId(existingPayment.getPay().getRecordid())
                .setRegDate(existingPayment.getPay().getLocaldate())
                .setAmount(existingPayment.getAmountcurr())
                .build();
    }

    private SberOnlineMessage responsePayment(long paymentId, double amount, String regDate) {
        return new SberOnlineMessage.Builder()
                .responseType(OK)
                .setExtId(paymentId)
                .setSum(amount)
                .setRegDate(regDate)
                .build();
    }

    private void createReceiptOrder(double amount, String orderNumber, String account, SoapAccountFull acctInfo) {
        eventBus.send("register-receipt", new ReceiptOrder(
                amount, orderNumber, account, UUID.randomUUID().toString(),
                new ReceiptCustomer(
                        acctInfo.getAccount().getEmail(),
                        acctInfo.getAccount().getMobile())));
    }
}
