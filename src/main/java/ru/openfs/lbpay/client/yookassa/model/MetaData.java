package ru.openfs.lbpay.client.yookassa.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaData(
    @JsonProperty("payment_id") String paymentId
) {   
}
