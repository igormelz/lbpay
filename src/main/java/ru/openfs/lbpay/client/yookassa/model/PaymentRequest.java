package ru.openfs.lbpay.client.yookassa.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record PaymentRequest(
    Amount amount,
    Boolean capture,
    @JsonProperty("payment_method_data") PaymentMethod paymentMethodData,
    Confirmation confirmation,
    String description,
    MetaData metadata
) {}
