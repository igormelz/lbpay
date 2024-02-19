package ru.openfs.lbpay.dto.yookassa;

public record YookassaPaymentRequest(
    YookassaAmount amount,
    Boolean capture,
    YookassaConfirmation confirmation,
    String description,
    YookassaMetaData metadata
) {}
