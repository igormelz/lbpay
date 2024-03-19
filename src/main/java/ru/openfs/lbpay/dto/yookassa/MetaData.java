package ru.openfs.lbpay.dto.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaData(
    @JsonProperty("payment_id") String paymentId
) {   
}
