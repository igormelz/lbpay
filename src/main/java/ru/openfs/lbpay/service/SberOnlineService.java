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

import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.resource.sberonline.exception.SberOnlineException;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineMessage;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineRequest;

@ApplicationScoped
public class SberOnlineService extends LbCoreService {

    @Inject
    EventBus eventBus;

    public SberOnlineMessage processRequest(SberOnlineRequest request) {
        if (request.isCheckOperation())
            return processCheckAccount(request.account());
        return processPayment(request);
    }

    private SberOnlineMessage processCheckAccount(String account) {
        final String sessionId = getSession();
        try {

            var acctInfo = lbCoreSoapClient.findAccountByAgrmNum(getSession(), account)
                    .orElseThrow(() -> new SberOnlineException(
                            ACCOUNT_NOT_FOUND, "not found account:" + account));

            return acctInfo.getAgreements().stream()
                    .filter(agrm -> agrm.getNumber().equalsIgnoreCase(account))
                    .filter(agrm -> agrm.getClosedon().isBlank())
                    .findFirst()
                    .map(agrm -> new SberOnlineMessage.Builder()
                            .responseType(OK)
                            .setBalance(agrm.getBalance())
                            .setRecSum(lbCoreSoapClient.getRecomendedPayment(sessionId, agrm.getAgrmid()))
                            .setAddress(acctInfo.getAddresses().isEmpty() ? null : acctInfo.getAddresses().get(0).getAddress())
                            .build())
                    .orElseThrow(() -> new SberOnlineException(ACCOUNT_INACTIVE, "check inactive account: " + account));

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                throw new SberOnlineException(ACCOUNT_NOT_FOUND, "account not found:" + account);
            }
            // propagate exception
            if (e instanceof SberOnlineException)
                throw e;
            // raise other
            throw new SberOnlineException(TMP_ERR, e.getMessage());
        } finally {
            closeSession(sessionId);
        }
    }

    private SberOnlineMessage processPayment(SberOnlineRequest request) {
        final String sessionId = getSession();
        try {
            // do payment
            var paymentId = lbCoreSoapClient.sberOnlinePayment(
                    getSession(), request.payId(), request.account(), request.amount(), request.payDate());

            // payment is ok? 
            return lbCoreSoapClient.findPayment(sessionId, request.payId())
                    .map(payment -> {
                        lbCoreSoapClient.findAccountByAgrmNum(sessionId, request.account())
                                .ifPresent(acct -> {
                                    // invoke receipt 
                                    eventBus.send("register-receipt",
                                            new ReceiptOrder(
                                                    request.amount(),
                                                    request.payId(),
                                                    request.account(),
                                                    UUID.randomUUID().toString(),
                                                    new ReceiptCustomer(
                                                            acct.getAccount().getEmail(),
                                                            acct.getAccount().getMobile())));
                                });
                        Log.infof("paid [%s], account:[%s], amount:[%.2f]",
                                request.payId(), request.account(), request.amount());
                        return new SberOnlineMessage.Builder()
                                .responseType(OK)
                                .setExtId(paymentId)
                                .setSum(request.amount())
                                .setRegDate(payment.getPay().getLocaldate())
                                .build();
                    })
                    .orElseThrow();

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found"))
                throw new SberOnlineException(ACCOUNT_NOT_FOUND, "account not found:" + request.account());

            if (e.getMessage().contains("already exists")) {
                Log.warnf("payment orderNumber: %s has already processed", request.payId());
                return lbCoreSoapClient.findPayment(sessionId, request.payId())
                        .map(p -> new SberOnlineMessage.Builder()
                                .responseType(PAY_TRX_DUPLICATE)
                                .setExtId(p.getPay().getRecordid())
                                .setRegDate(p.getPay().getLocaldate())
                                .setAmount(p.getAmountcurr())
                                .build())
                        .orElseThrow(() -> new SberOnlineException(TMP_ERR, e.getMessage()));
            }

            eventBus.send("notify-error", String.format("payment orderNumber: %s - %s", request.payId(), e.getMessage()));
            throw new SberOnlineException(TMP_ERR, e.getMessage());

        } finally {
            closeSession(sessionId);
        }
    }
}
