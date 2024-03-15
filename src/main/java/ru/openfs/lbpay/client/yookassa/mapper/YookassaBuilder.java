package ru.openfs.lbpay.client.yookassa.mapper;

import ru.openfs.lbpay.client.yookassa.model.Amount;
import ru.openfs.lbpay.client.yookassa.model.Confirmation;
import ru.openfs.lbpay.client.yookassa.model.MetaData;
import ru.openfs.lbpay.client.yookassa.model.PaymentMethod;
import ru.openfs.lbpay.client.yookassa.model.PaymentRequest;

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
