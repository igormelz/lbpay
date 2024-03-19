package ru.openfs.lbpay.dto.yookassa;

public record Webhook(
    String type,
    String event,
    Payment object
) {
    
}
