package ru.openfs.lbpay.dto.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YookassaRecepient(
    @JsonProperty("account_id") String accountId,
    @JsonProperty("gateway_id") String gatewayId
) {}
