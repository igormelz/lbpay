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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.openfs.lbpay.client.dreamkas.DreamkasClient;
import ru.openfs.lbpay.exception.PaymentException;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.dreamkas.Operation;
import ru.openfs.lbpay.model.dreamkas.Receipt;
import ru.openfs.lbpay.model.entity.DreamkasOperation;
import ru.openfs.lbpay.service.ReceiptOperation;
import ru.openfs.lbpay.service.ReceiptService;

@ApplicationScoped
public class DreamkasReceiptService implements ReceiptService, ReceiptOperation {
    private static final Pattern EMAIL = Pattern.compile("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[1-9]\\d{10,13}+$");

    @ConfigProperty(name = "dreamkas.deviceId", defaultValue = "123456")
    Integer deviceId;

    @ConfigProperty(name = "dreamkas.receipt.product.name", defaultValue = "Пополнение счета")
    String productName;

    @Inject
    EventBus eventBus;

    @Inject
    @RestClient
    DreamkasClient dreamkasClient;

    /**
     * register receipt on OFD. invoke async by success payment or by bot command 'reg_receipt'
     * 
     * @param receiptOrder the receipt order
     */
    @ConsumeEvent(value = "register-receipt", blocking = true)
    @Transactional
    public void registerReceipt(ReceiptOrder receiptOrder) {
        Log.debugf("Start registering receiptOrder: %s", receiptOrder);

        var receiptOperation = getOrCreateOperation(receiptOrder);

        if (receiptOperation.isPersistent()) {
            try {
                // call to register
                var receipt = createReceipt(receiptOrder);
                Log.debugf("Create receipt: %s", receipt);

                var response = dreamkasClient.register(receipt);
                Optional.ofNullable(response).ifPresentOrElse(it -> {
                    updateReceiptOperation(receiptOperation, it);
                    Log.infof("Receipt for [%s]: %s with operation: %s", receiptOrder.orderNumber(), it.status(), it.id());
                }, () -> Log.error("no response"));

            } catch (RuntimeException e) {
                eventBus.send("notify-error", e.getMessage());
            }
        }
    }

    private boolean isValid(ReceiptCustomer info) {
        return (info.email() != null && EMAIL.matcher(info.email()).matches())
                || (info.phone() != null && PHONE.matcher(info.phone()).matches());
    }

    private Receipt createReceipt(ReceiptOrder receiptOrder) {

        if (!isValid(receiptOrder.info()))
            throw new PaymentException(
                    String.format("wrong %s for [%s] account:[%s]", receiptOrder.info(), receiptOrder.orderNumber(),
                            receiptOrder.account()));

        // calc service price to coins
        var price = (int) (receiptOrder.amount() * 100);

        return new Receipt(receiptOrder.mdOrder(), deviceId, price, receiptOrder.info().email(), receiptOrder.info().phone(), productName);
    }

    /**
     * call dk to get operation status
     * 
     * @param  operationId
     * @return Operation
     */
    @ConsumeEvent("get-register-status")
    public Operation getOperation(String operationId) {
        try {
            return dreamkasClient.getOperation(operationId);
        } catch (RuntimeException e) {
            eventBus.send("notify-error", e.getMessage());
            return null;
        }
    }

    /**
     * process audit operation
     * 
     * @param operation
     */
    @Transactional
    public void processOperation(Operation operation) {
        DreamkasOperation.findByExternalId(operation.externalId()).ifPresent(
                receiptOperation -> {
                    switch (operation.status()) {
                        case ERROR -> {
                            eventBus.send("notify-error",
                                    String.format("receipt for [%s]: %s", receiptOperation.orderNumber,
                                            operation.data().error().message()));
                            updateReceiptOperation(receiptOperation, operation);
                        }
                        case SUCCESS -> {
                            Log.infof("Receipt for [%s]: %s with operation: %s", receiptOperation.orderNumber,
                                    operation.status(), operation.id());
                            receiptOperation.delete();
                        }
                        default -> updateReceiptOperation(receiptOperation, operation);
                    }

                });
    }

    private DreamkasOperation getOrCreateOperation(ReceiptOrder receiptOrder) {
        return DreamkasOperation.findByOrderNumber(receiptOrder.orderNumber()).orElseGet(() -> {
            var receiptOperation = new DreamkasOperation();
            receiptOperation.account = receiptOrder.account();
            receiptOperation.amount = receiptOrder.amount();
            receiptOperation.createAt = LocalDateTime.now();
            receiptOperation.email = receiptOrder.info().email();
            receiptOperation.phone = receiptOrder.info().phone();
            receiptOperation.externalId = receiptOrder.mdOrder();
            receiptOperation.orderNumber = receiptOrder.orderNumber();
            receiptOperation.persistAndFlush();
            return receiptOperation;
        });
    }

    private void updateReceiptOperation(DreamkasOperation receiptOperation, Operation operation) {
        receiptOperation.operationId = operation.id();
        receiptOperation.operationStatus = operation.status();
        receiptOperation.persist();
        if (receiptOperation.isPersistent())
            Log.debug("update operation");
        else
            Log.error("operation not updated");
    }

}
