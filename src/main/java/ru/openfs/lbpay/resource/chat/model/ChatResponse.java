package ru.openfs.lbpay.resource.chat.model;

public record WaitingReceiptsResponse(
        List<ChatReceiptOperation> receiptOperations
) {
}
