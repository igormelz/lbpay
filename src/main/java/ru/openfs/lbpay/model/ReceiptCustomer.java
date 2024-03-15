package ru.openfs.lbpay.model;

public record ReceiptCustomer(String email, String phone) {
    public ReceiptCustomer(String email, String phone) {
        this.email = (email != null && email.isBlank()) ? null : email;
        if (phone != null && phone.isBlank()) {
            this.phone = null;
        } else if (phone != null && !phone.startsWith("+")) {
            this.phone = "+" + phone;
        } else {
            this.phone = phone;
        }
    }
}
