package ru.openfs.lbpay.dto.yookassa;

public record PaymentRequest(
    Amount amount,
    Boolean capture,
    Confirmation confirmation,
    String description,
    MetaData metadata
) {}
