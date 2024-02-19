package ru.openfs.lbpay.dto.yookassa;

public record YookassaWebhook(
    String type,
    String event,
    YookassaPayment object
) {
    
}
