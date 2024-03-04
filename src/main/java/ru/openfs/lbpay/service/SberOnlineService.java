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

import java.util.Optional;
import java.util.UUID;

import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.LbCoreSoapClient;
import ru.openfs.lbpay.exception.SberOnlineException;
import ru.openfs.lbpay.mapper.ReceiptOrderBuilder;
import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.SberOnlineCheckResponse;
import ru.openfs.lbpay.model.SberOnlinePaymentResponse;
import ru.openfs.lbpay.model.SberOnlineRequest;

import static ru.openfs.lbpay.model.type.SberOnlineResponseCode.*;

@ApplicationScoped
public class SberOnlineService {

    @Inject
    LbCoreSoapClient lbSoapClient;

    @Inject
    EventBus eventBus;

    // connect to billing
    protected String getSession() {
        return Optional.ofNullable(lbSoapClient.login())
                .orElseThrow(() -> new SberOnlineException(TMP_ERR, "no lb session"));
    }

    /**
     * check active account
     * 
     * @param  account the account number to test
     * @return         current balance, recommended pay and address
     */
    public SberOnlineCheckResponse processCheckAccount(String account) {
        final String sessionId = getSession();
        try {

            var acctInfo = lbSoapClient.findAccountByAgrmNum(sessionId, account)
                    .orElseThrow(() -> new SberOnlineException(
                            ACCOUNT_NOT_FOUND, "not found account:" + account));

            return acctInfo.getAgreements().stream()
                    .filter(agrm -> agrm.getNumber().equalsIgnoreCase(account))
                    .filter(agrm -> agrm.getClosedon().isBlank())
                    .findFirst().map(agrm -> new SberOnlineCheckResponse(
                            agrm.getBalance(), lbSoapClient.getRecomendedPayment(sessionId, agrm.getAgrmid()),
                            (acctInfo.getAddresses().isEmpty()) ? null : acctInfo.getAddresses().get(0).getAddress()))
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
            lbSoapClient.logout(sessionId);
        }
    }

    public SberOnlinePaymentResponse processPayment(SberOnlineRequest request) {
        final String sessionId = getSession();
        try {
            // do payment
            var paymentId = lbSoapClient.sberOnlinePayment(sessionId, request.payId(), request.account(), request.amount(),
                    request.payDate());

            // payment is ok? 
            return lbSoapClient.findPayment(sessionId, request.payId())
                    .map(payment -> {
                        // invoke receipt 
                        lbSoapClient.findAccountByAgrmNum(sessionId, request.account()).ifPresent(acct -> {
                            eventBus.send("register-receipt",
                                    ReceiptOrderBuilder.createReceiptOrder(UUID.randomUUID().toString(), request.payId(),
                                            request.amount(), request.account(),
                                            acct.getAccount().getEmail(), acct.getAccount().getMobile()));
                        });

                        Log.infof("paid orderNumber:[%s], account:[%s], amount:[%.2f]",
                                request.payId(), request.account(), request.amount());
                        return new SberOnlinePaymentResponse(
                                paymentId, request.amount(), payment.getPay().getLocaldate(), null);
                    })
                    .orElseThrow();

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found"))
                throw new SberOnlineException(ACCOUNT_NOT_FOUND, "account not found:" + request.account());

            if (e.getMessage().contains("already exists")) {
                Log.warnf("payment orderNumber: %s has already processed", request.payId());
                return lbSoapClient.findPayment(sessionId, request.payId())
                        .map(p -> new SberOnlinePaymentResponse(
                                p.getPay().getRecordid(), null, p.getPay().getLocaldate(), p.getAmountcurr()))
                        .orElseThrow(() -> new SberOnlineException(TMP_ERR, e.getMessage()));
            }

            eventBus.send("notify-error", String.format("payment orderNumber: %s - %s", request.payId(), e.getMessage()));
            throw new SberOnlineException(TMP_ERR, e.getMessage());

        } finally {
            lbSoapClient.logout(sessionId);
        }
    }
}
