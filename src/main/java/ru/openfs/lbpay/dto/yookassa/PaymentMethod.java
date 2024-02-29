package ru.openfs.lbpay.dto.yookassa;

public record PaymentMethod(
    String type,
    String id,
    Boolean saved
) {}
