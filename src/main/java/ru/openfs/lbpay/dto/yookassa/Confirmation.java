package ru.openfs.lbpay.dto.yookassa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record Confirmation(
    String type,
    @JsonProperty("return_url") String returnUrl,
    @JsonProperty("confirmation_url") String confirmationUrl
) {}
