package ru.openfs.lbpay.client.yookassa.model;

public record Webhook(
    String type,
    String event,
    Payment object
) {
    
}
