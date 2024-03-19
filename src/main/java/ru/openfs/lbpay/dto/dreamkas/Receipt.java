package ru.openfs.lbpay.dto.dreamkas;

import java.util.List;

import ru.openfs.lbpay.dto.dreamkas.type.OperationType;
import ru.openfs.lbpay.dto.dreamkas.type.TaxMode;

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