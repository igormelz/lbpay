package ru.openfs.lbpay.client.dreamkas.model;

import ru.openfs.lbpay.client.dreamkas.model.type.PaymentType;

public record Payment(Integer sum, PaymentType type) {}
