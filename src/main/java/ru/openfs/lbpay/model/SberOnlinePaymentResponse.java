package ru.openfs.lbpay.model;

public record SberOnlinePaymentResponse(Long paymentId, Double paymentSum, String regDate, Double amount) {
}
