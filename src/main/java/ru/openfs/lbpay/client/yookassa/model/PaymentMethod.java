package ru.openfs.lbpay.client.yookassa.model;

public record PaymentMethod(
    String type,
    String id,
    Boolean saved
) {}
