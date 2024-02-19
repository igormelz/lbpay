package ru.openfs.lbpay.dto.yookassa;

public record YookassaPaymentMethod(
    String type,
    String id,
    Boolean saved
) {}
