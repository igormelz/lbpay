package ru.openfs.lbpay.client.yookassa.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Recepient(
    @JsonProperty("account_id") String accountId,
    @JsonProperty("gateway_id") String gatewayId
) {}
