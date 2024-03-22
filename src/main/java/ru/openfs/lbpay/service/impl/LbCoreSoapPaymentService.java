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
package ru.openfs.lbpay.service.impl;

import api3.SoapAccountFull;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;
import ru.openfs.lbpay.exception.PaymentException;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.service.PaymentService;

@ApplicationScoped
public class LbCoreSoapPaymentService implements PaymentService {

    @Inject
    LbCoreSoapClient lbCoreSoapClient;

    @Inject
    EventBus eventBus;

    /**
     * process payment by orderNumber
     * 
     * @param orderNumber the orderNumber to paid
     * @param mdOrder     the orderNumber reference id
     */
    public void processPayment(Long orderNumber, String mdOrder) {
        Log.debugf("start payment for:[%d]", orderNumber);
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {

            var order = adapter.findPrePaymentByOrderNumber(orderNumber)
                    .orElseThrow(() -> new PaymentException("orderNumber not found for:[" + orderNumber + "]"));

            // test duplicate 
            if (order.getStatus() != 0) {
                Log.warnf("orderNumber:[" + orderNumber + "] was paid at " + order.getPaydate());
                return;
            }

            // do payment
            adapter.confirmPrePayment(orderNumber, order.getAmount(), mdOrder);

            var acct = adapter.findAccountByAgreementId(order.getAgrmid()).orElseThrow();
            var agrm = acct.getAgreements().stream().filter(a -> a.getAgrmid() == order.getAgrmid()).findFirst()
                    .orElseThrow();
            Log.infof("Process payment orderNumber:[%d], account:[%s], amount:[%.2f]",
                    orderNumber, agrm.getNumber(), order.getAmount());

            createReceiptOrder(order.getAmount(), String.valueOf(orderNumber), agrm.getNumber(), mdOrder, acct);

        } catch (Exception e) {
            eventBus.send("notify-error", String.format("orderNumber:[%d] deposited: %s", orderNumber, e.getMessage()));
            throw new PaymentException(e.getMessage());
        }
    }

    /**
     * process decline orderNumber
     * 
     * @param orderNumber the orderNumber to decline
     */
    public void processDecline(long orderNumber) {
        Log.debugf("start decline orderNumber: [%d]", orderNumber);
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {

            var order = adapter.findPrePaymentByOrderNumber(orderNumber)
                    .orElseThrow(() -> new PaymentException("not found orderNumber:[" + orderNumber + "]"));

            if (order.getStatus() != 0) {
                Log.warnf("orderNumber:[" + orderNumber + "] was declined at " + order.getPaydate());
                return;
            }

            // do cancel
            adapter.cancelPrePayment(orderNumber);
            Log.infof("declined orderNumber:[%d]", orderNumber);

        } catch (Exception e) {
            eventBus.send("notify-error",
                    String.format("declined orderNumber:[%d] - %s", orderNumber, e.getMessage()));
            throw new PaymentException(e.getMessage());
        }
    }

    private void createReceiptOrder(
            double amount, String orderNumber, String account, String externalId, SoapAccountFull acctInfo) {
        eventBus.send("register-receipt", new ReceiptOrder(
                amount, orderNumber, account, externalId,
                new ReceiptCustomer(
                        acctInfo.getAccount().getEmail(),
                        acctInfo.getAccount().getMobile())));
    }
}
