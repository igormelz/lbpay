package ru.openfs.lbpay.dto.sberbank;

public record SberRegisterResponse(
    String orderId,
    String formUrl,
    Integer errorCode,
    String errorMessage
) {
    
}
