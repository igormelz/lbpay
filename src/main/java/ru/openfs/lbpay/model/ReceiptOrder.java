package ru.openfs.lbpay.model;

public record ReceiptOrder(
    Double amount,
    String orderNumber,
    String account,
    String mdOrder,
    ReceiptCustomerInfo info
){}

