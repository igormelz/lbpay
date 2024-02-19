package ru.openfs.lbpay.mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import ru.openfs.lbpay.dto.dreamkas.Receipt;
import ru.openfs.lbpay.dto.dreamkas.ReceiptAttributes;
import ru.openfs.lbpay.dto.dreamkas.ReceiptPayment;
import ru.openfs.lbpay.dto.dreamkas.ReceiptPosition;
import ru.openfs.lbpay.dto.dreamkas.ReceiptTotal;
import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.SberOnlineRequest;

public class ReceiptMapper {
    private static final Pattern EMAIL = Pattern.compile("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[1-9]\\d{10,13}+$");

    private ReceiptMapper() {
    }

    public static ReceiptOrder createReceiptOrder(SberOnlineRequest request, ReceiptCustomerInfo info) {
        return new ReceiptOrder(
                request.amount(),
                request.payId(),
                request.account(),
                UUID.randomUUID().toString(),
                info);
    }

    public static boolean isValid(ReceiptCustomerInfo info) {
        return Optional.ofNullable(info.email()).map(email -> EMAIL.matcher(info.email()).matches())
                .orElse(Optional.ofNullable(info.phone()).map(phone -> PHONE.matcher(phone).matches()).orElse(false));
    }

    public static Receipt createReceipt(ReceiptOrder receiptOrder, Integer deviceId) {
        // calc service price
        var price = (long) (receiptOrder.amount() * 100);
        // return receipt object
        return new Receipt(
                receiptOrder.mdOrder(),
                deviceId,
                "SALE",
                5,
                "SIMPLE_WO",
                List.of(new ReceiptPosition(
                        "Оплата услуг",
                        "SERVICE",
                        1,
                        price,
                        price,
                        "NDS_NO_TAX",
                        0)),
                List.of(new ReceiptPayment(
                        price,
                        "CASHLESS")),
                new ReceiptAttributes(
                        receiptOrder.info().email(),
                        receiptOrder.info().phone()),
                new ReceiptTotal(price));
    }
}
