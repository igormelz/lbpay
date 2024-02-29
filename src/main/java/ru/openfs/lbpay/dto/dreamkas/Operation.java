package ru.openfs.lbpay.dto.dreamkas;

import ru.openfs.lbpay.dto.dreamkas.type.OperationStatus;

public record Operation(
    String id,
    String externalId,
    String createdAt,
    OperationStatus status,
    OperationData data
) {
    
}
