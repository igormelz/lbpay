package ru.openfs.lbpay.client.dreamkas.model;

import java.util.List;

import ru.openfs.lbpay.client.dreamkas.model.type.OperationType;
import ru.openfs.lbpay.client.dreamkas.model.type.TaxMode;

public record Receipt(
    String externalId,
    Integer deviceId,
    OperationType type,
    Integer timeout,
    TaxMode taxMode,
    List<Position> positions,
    List<Payment> payments,
    Attributes attributes,
    Total total
) {}