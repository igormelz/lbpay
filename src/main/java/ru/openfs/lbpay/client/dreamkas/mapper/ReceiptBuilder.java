package ru.openfs.lbpay.client.dreamkas.mapper;

import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.client.dreamkas.model.Attributes;
import ru.openfs.lbpay.client.dreamkas.model.Payment;
import ru.openfs.lbpay.client.dreamkas.model.Position;
import ru.openfs.lbpay.client.dreamkas.model.Receipt;
import ru.openfs.lbpay.client.dreamkas.model.Total;
import ru.openfs.lbpay.client.dreamkas.model.type.OperationType;
import ru.openfs.lbpay.client.dreamkas.model.type.PaymentType;
import ru.openfs.lbpay.client.dreamkas.model.type.PositionType;
import ru.openfs.lbpay.client.dreamkas.model.type.TaxMode;
import ru.openfs.lbpay.client.dreamkas.model.type.VatType;
import ru.openfs.lbpay.exception.PaymentException;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;

@ApplicationScoped
public class ReceiptBuilder {
    private static final Pattern EMAIL = Pattern.compile("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[1-9]\\d{10,13}+$");

    @ConfigProperty(name = "dreamkas.deviceId", defaultValue = "123456")
    Integer deviceId;

    public boolean isValid(ReceiptCustomer info) {
        return (info.email() != null && EMAIL.matcher(info.email()).matches())
                || (info.phone() != null && PHONE.matcher(info.phone()).matches());
    }

    public Receipt createReceipt(ReceiptOrder receiptOrder) {

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
