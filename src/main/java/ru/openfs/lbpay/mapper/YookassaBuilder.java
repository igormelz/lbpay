package ru.openfs.lbpay.mapper;

import ru.openfs.lbpay.dto.yookassa.Amount;
import ru.openfs.lbpay.dto.yookassa.Confirmation;
import ru.openfs.lbpay.dto.yookassa.MetaData;
import ru.openfs.lbpay.dto.yookassa.PaymentMethod;
import ru.openfs.lbpay.dto.yookassa.PaymentRequest;

public class YookassaBuilder {
    YookassaBuilder() {}

    public static PaymentRequest createRequest(Long orderNumber, String account, Double amount, String url) {
        return new PaymentRequest(
                new Amount(Double.toString(amount), "RUB"),
                true,
                new PaymentMethod("bank_card", null, null),
                new Confirmation("redirect", url, null),
                String.format("Оплата заказа №%d по договору %s", orderNumber, account),
                new MetaData(Long.toString(orderNumber))
        );
    }
}
