package ru.openfs.lbpay.client.yookassa.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Payment(
    String id, 
    String status,
    Amount amount, 
    @JsonProperty("income_amount") Amount incomeAmount, 
    String description,
    Recepient recepient,
    @JsonProperty("payment_method") PaymentMethod paymentMethod,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("captured_at") String capturedAt,
    @JsonProperty("expires_at") String expiresAt, 
    Confirmation confirmation,
    MetaData metadata,
    Boolean test, Boolean paid, Boolean refundable) {}
