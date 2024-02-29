package ru.openfs.lbpay.service;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.DreamkasClient;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.mapper.ReceiptBuilder;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.repository.AuditDAO;

@ApplicationScoped
public class ReceiptService {

    @ConfigProperty(name = "dreamkas.deviceId")
    Integer deviceId;

    @Inject
    EventBus eventBus;

    @Inject
    AuditDAO auditRepository;

    @RestClient
    DreamkasClient dreamkasClient;

    /**
     * register receipt on OFD
     * 
     * @param receiptOrder the receipt order
     */
    @ConsumeEvent("register-receipt")
    public void registerReceipt(ReceiptOrder receiptOrder) {
        Log.debugf("Start registering receiptOrder: %s", receiptOrder);

        auditRepository.createOperation(receiptOrder);

        try {
            var receipt = ReceiptBuilder.createReceipt(receiptOrder, deviceId);
            Log.debugf("Create receipt: %s", receipt);

            var response = dreamkasClient.register(receipt);
            Log.infof("Receipt for [%s]: %s with operation: %s", receiptOrder.orderNumber(),
                    response.status(), response.id());

            auditRepository.updateOperation(response);
        } catch (RuntimeException e) {
            Log.error(e.getMessage());
            eventBus.send("notify-error", e.getMessage());
        }
    }

    public void processReceiptOperation(Operation operation) {
        // process audit record
        auditRepository.findById(operation.externalId()).subscribe().with(
                auditRecord -> {
                    switch (operation.status()) {
                        case ERROR -> {
                            eventBus.send("notify-error",
                                    String.format("receipt for [%s]: %s", auditRecord.orderNumber(),
                                            operation.data().error().message()));
                            auditRepository.updateOperation(operation);
                        }
                        case SUCCESS -> {
                            Log.infof("Receipt for [%s]: %s with operation: %s", auditRecord.orderNumber(),
                                    operation.status(), operation.id());
                            auditRepository.deleteOperation(operation);
                        }
                        default -> auditRepository.updateOperation(operation);
                    }
                },
                fail -> Log.warn("not found receipt operation by id:" + operation.externalId()));
    }

    // @ConsumeEvent("dk-register-status")
    // public Uni<JsonObject> getOperation(@PathParam("id") String operId) {
    //     return client.get("/api/operations/" + operId).putHeader("Authorization", "Bearer " + token).send()
    //             .onItem().transform(HttpResponse::bodyAsJsonObject);
    // }

}
