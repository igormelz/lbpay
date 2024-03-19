package ru.openfs.lbpay.mapper;

import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

public class ReceiptOrderBuilder {
    ReceiptOrderBuilder() {}

    public static ReceiptOrder createReceiptOrderFromOperation(DreamkasOperation operation) {
        return new ReceiptOrder(
                operation.amount, operation.orderNumber, operation.account, operation.externalId,
                new ReceiptCustomerInfo(operation.email, operation.phone));
    }

    public static ReceiptOrder createReceiptOrder(
            String mdOrder, String orderNumber, Double amount, String account, String email, String phone) {
        return new ReceiptOrder(
                amount, orderNumber, account, mdOrder,
                new ReceiptCustomerInfo(email, phone));
    }
}
