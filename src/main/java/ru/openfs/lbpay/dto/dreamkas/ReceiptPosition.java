package ru.openfs.lbpay.dto.dreamkas;

public record ReceiptPosition(
    String name, 
    String type, 
    Integer quantity, 
    Long price, 
    Long priceSum,
    String tax,
    Integer taxSum
){}