package ru.openfs.lbpay.dto.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YookassaPayment(
    String id, 
    String status, 
    YookassaAmount amount, 
    @JsonProperty("income_amount") YookassaAmount incomeAmount, 
    String description,
    YookassaRecepient recepient,
    @JsonProperty("payment_method") YookassaPaymentMethod paymentMethod,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("captured_at") String capturedAt,
    @JsonProperty("expires_at") String expiresAt, 
    YookassaConfirmation confirmation,
    YookassaMetaData metadata,
    Boolean test, Boolean paid, Boolean refundable) {}
