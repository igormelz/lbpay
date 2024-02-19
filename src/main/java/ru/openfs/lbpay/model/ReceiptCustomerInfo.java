package ru.openfs.lbpay.model;

public record ReceiptCustomerInfo(String email, String phone) {
    public ReceiptCustomerInfo
    (String email, String phone) {
        this.email = email;
        this.phone = (phone != null && !phone.startsWith("+")) ? "+" + phone : phone;
    }
}
