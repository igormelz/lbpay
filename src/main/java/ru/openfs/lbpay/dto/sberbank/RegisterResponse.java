package ru.openfs.lbpay.dto.sberbank;

public record RegisterResponse(
    String orderId,
    String formUrl,
    Integer errorCode,
    String errorMessage
) {
    
}
