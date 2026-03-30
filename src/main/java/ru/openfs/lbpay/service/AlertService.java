package ru.openfs.lbpay.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.openfs.lbpay.client.chat.ChatClient;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.chat.IncomingWebhook;
import ru.openfs.lbpay.model.dreamkas.type.OperationStatus;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

@ApplicationScoped
public class AlertService {

    @ConfigProperty(name = "alert.token")
    String token;

    @RestClient
    ChatClient chatClient;

    @Inject
    EventBus eventBus;

    @ConsumeEvent(value = "notify-error", blocking = true)
    public void sendAlert(String message) {
        Log.errorf("sendAlert: [%s]", message);
        if(!message.isBlank()) chatClient.post(token, new IncomingWebhook(message));
    }

    @Transactional
    public String getWaitingReceipts() {
        List<DreamkasOperation> receipts = DreamkasOperation.listAll(Sort.by("createAt"));

        if (receipts.isEmpty()) {
            return "🆗 no waiting receipts";
        }

        String header = """
                ## waiting receipts
                |orderNumber|status|opId|
                |:---|:---|:---|
                """;
        String rows = receipts.stream()
                .map(r -> "|%s|%s|%s|".formatted(
                        r.orderNumber,
                        Optional.ofNullable(r.operationStatus).map(it -> it.name()).orElse("NO REG"),
                        r.operationId))
                .collect(Collectors.joining("\n"));

        return header + rows;
    }

    @Transactional
    public String doRegisterReceipt(String orderNumber) {
        return DreamkasOperation.findByOrderNumber(orderNumber)
                .map(it -> {
                    if (it.operationId == null || it.operationStatus == OperationStatus.ERROR) {
                        var answer = "re-processing orderNumber: %s".formatted(orderNumber);
                        Log.info(answer);
                        eventBus.send("register-receipt",
                                new ReceiptOrder(
                                        it.amount,
                                        it.orderNumber,
                                        it.account,
                                        it.externalId,
                                        new ReceiptCustomer(
                                                it.email,
                                                it.phone),
                                        null));
                        return answer;
                    } else {
                        return "orderNumber %s can not reporcessed".formatted(orderNumber);
                    }
                })
                .orElse("orderNumber %s not found".formatted(orderNumber));
    }

}