package ru.openfs.lbpay.model;

import ru.openfs.lbpay.model.type.SberOnlineOperation;

public record SberOnlineRequest(
    SberOnlineOperation operation,
    String account,
    Double amount,
    String payId,
    String payDate
) {
    
}
