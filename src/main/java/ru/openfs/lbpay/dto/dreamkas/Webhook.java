package ru.openfs.lbpay.dto.dreamkas;

import io.vertx.core.json.JsonObject;
import ru.openfs.lbpay.dto.dreamkas.type.WebhookAction;
import ru.openfs.lbpay.dto.dreamkas.type.WebhookType;

public record Webhook(
    WebhookAction action,
    WebhookType type,
    JsonObject data
) {
    
}
