package ru.openfs.lbpay.client.sberbank.model;

public record RegisterResponse(
    String orderId,
    String formUrl,
    Integer errorCode,
    String errorMessage
) {
    
}
