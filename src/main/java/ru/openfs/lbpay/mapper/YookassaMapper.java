package ru.openfs.lbpay.mapper;

import ru.openfs.lbpay.dto.yookassa.YookassaAmount;
import ru.openfs.lbpay.dto.yookassa.YookassaConfirmation;
import ru.openfs.lbpay.dto.yookassa.YookassaMetaData;
import ru.openfs.lbpay.dto.yookassa.YookassaPaymentRequest;

public class YookassaMapper {
    YookassaMapper() {}

    public static YookassaPaymentRequest createRequest(Long orderNumber, String account, Double amount, String url) {
        return new YookassaPaymentRequest(
                new YookassaAmount(Double.toString(amount), "RUB"),
                true,
                new YookassaConfirmation("redirect", url, null),
                String.format("Оплата заказа №%d по договору %s", orderNumber, account),
                new YookassaMetaData(Long.toString(orderNumber))
        );
    }
}
