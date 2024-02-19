package ru.openfs.lbpay.dto.dreamkas;

import java.util.List;

public record Receipt(
    String externalId,
    Integer deviceId,
    String type,
    Integer timeout,
    String taxMode,
    List<ReceiptPosition> positions,
    List<ReceiptPayment> payments,
    ReceiptAttributes attributes,
    ReceiptTotal total
) {}