package ru.openfs.lbpay.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.openfs.lbpay.client.DreamkasClient;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.mapper.ReceiptBuilder;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

@ApplicationScoped
public class ReceiptService {

    @ConfigProperty(name = "dreamkas.deviceId")
    Integer deviceId;

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
                var receipt = ReceiptBuilder.createReceipt(receiptOrder, deviceId);
                Log.debugf("Create receipt: %s", receipt);

                var response = dreamkasClient.register(receipt);
                Optional.ofNullable(response).ifPresentOrElse(it -> {
                    // update auditEntry
                    updateReceiptOperation(receiptOperation, response);
                    Log.infof("Receipt for [%s]: %s with operation: %s", receiptOrder.orderNumber(), it.status(), it.id());
                }, () -> Log.error("no response"));

            } catch (RuntimeException e) {
                eventBus.send("notify-error", e.getMessage());
            }
        }
    }

    /**
     * call dk to get operation status 
     * 
     * @param operationId
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
    public void processReceiptOperation(Operation operation) {
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
