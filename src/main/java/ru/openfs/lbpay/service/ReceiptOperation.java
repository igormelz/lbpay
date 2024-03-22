package ru.openfs.lbpay.service;

import ru.openfs.lbpay.model.dreamkas.Operation;

public interface ReceiptOperation {
    Operation getOperation(String operationId);
    void processOperation(Operation operation);
}
