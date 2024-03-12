package ru.openfs.lbpay.dto.dreamkas;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import ru.openfs.lbpay.dto.dreamkas.type.OperationStatus;

@JsonInclude(Include.NON_NULL)
public record Operation(
    String id,
    String externalId,
    String createdAt,
    OperationStatus status,
    OperationData data,
    String type,
    String completedAt
) {
    
}
