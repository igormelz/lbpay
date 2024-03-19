package ru.openfs.lbpay.mapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import ru.openfs.lbpay.dto.dreamkas.Receipt;
import ru.openfs.lbpay.dto.dreamkas.Attributes;
import ru.openfs.lbpay.dto.dreamkas.Payment;
import ru.openfs.lbpay.dto.dreamkas.Position;
import ru.openfs.lbpay.dto.dreamkas.Total;
import ru.openfs.lbpay.dto.dreamkas.type.OperationType;
import ru.openfs.lbpay.dto.dreamkas.type.PaymentType;
import ru.openfs.lbpay.dto.dreamkas.type.PositionType;
import ru.openfs.lbpay.dto.dreamkas.type.TaxMode;
import ru.openfs.lbpay.dto.dreamkas.type.VatType;
import ru.openfs.lbpay.exception.PaymentException;
import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;

public class ReceiptBuilder {
    private static final Pattern EMAIL = Pattern.compile("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[1-9]\\d{10,13}+$");

    private ReceiptBuilder() {}

    static boolean isValid(ReceiptCustomerInfo info) {
        return (info.email() != null && EMAIL.matcher(info.email()).matches())
                || (info.phone() != null && PHONE.matcher(info.phone()).matches());
    }

    public static Receipt createReceipt(ReceiptOrder receiptOrder, Integer deviceId) {

        if (!isValid(receiptOrder.info()))
            throw new PaymentException(
                    "wrong " + receiptOrder.info() + " for [" + receiptOrder.orderNumber() + "] account:["
                                       + receiptOrder.account() + "]");

        // calc service price to coins
        var price = (int) (receiptOrder.amount() * 100);

        return new Receipt(
                receiptOrder.mdOrder(), deviceId, OperationType.SALE, 15, TaxMode.SIMPLE_WO,
                List.of(new Position(
                        "Оплата услуг", PositionType.SERVICE, 1, price, price,
                        VatType.NDS_NO_TAX, 0)),
                List.of(new Payment(price, PaymentType.CASHLESS)),
                new Attributes(receiptOrder.info().email(), receiptOrder.info().phone()),
                new Total(price));
    }
}
