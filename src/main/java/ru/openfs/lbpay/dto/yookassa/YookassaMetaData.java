package ru.openfs.lbpay.dto.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YookassaMetaData(
    @JsonProperty("payment_id") String paymentId
) {   
}
