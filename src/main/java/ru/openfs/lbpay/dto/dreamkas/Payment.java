package ru.openfs.lbpay.dto.dreamkas;

import ru.openfs.lbpay.dto.dreamkas.type.PaymentType;

public record Payment(Integer sum, PaymentType type) {}
