package ru.openfs.lbpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.openfs.lbpay.dto.dreamkas.type.OperationType;
import ru.openfs.lbpay.dto.dreamkas.type.PaymentType;
import ru.openfs.lbpay.dto.dreamkas.type.PositionType;
import ru.openfs.lbpay.dto.dreamkas.type.TaxMode;
import ru.openfs.lbpay.dto.dreamkas.type.VatType;
import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;

class ReceiptBuilderTest {
    @Test
    void testIsValid() {
        var ok = new ReceiptCustomerInfo("a@b.com", "79001234567");
        assertEquals(true, ReceiptBuilder.isValid(ok));
        assertEquals("+79001234567", ok.phone());

        var phone = new ReceiptCustomerInfo("", "+79001234567");
        assertEquals(true, ReceiptBuilder.isValid(phone));
        assertEquals("+79001234567", phone.phone());

        var email = new ReceiptCustomerInfo("1@a.com", "");
        assertEquals(true, ReceiptBuilder.isValid(email));
        assertEquals("1@a.com", email.email());

        var empty = new ReceiptCustomerInfo(null, null);
        assertEquals(false, ReceiptBuilder.isValid(empty));

        var badPhone = new ReceiptCustomerInfo(null, "12345");
        assertEquals(false, ReceiptBuilder.isValid(badPhone));

        var badEmail = new ReceiptCustomerInfo("1@1", null);
        assertEquals(false, ReceiptBuilder.isValid(badEmail));
    }

    @Test
    void testCreateReceipt() {
        var order = new ReceiptOrder(1.23, "orderNumber", "123456", "mdOrder", new ReceiptCustomerInfo(null, "79001234567"));
        var rcpt = ReceiptBuilder.createReceipt(order, 123);
        /*
         * {
         *      "externalId":"mdOrder",
         *      "deviceId":123,
         *      "type":"SALE",
         *      "timeout":5,
         *      "taxMode":"SIMPLE_WO",
         *      "positions":[{
         *          "name":"Оплата услуг",
         *          "type":"SERVICE",
         *          "quantity":1,
         *          "price":123,
         *          "priceSum":123,
         *          "tax":"NDS_NO_TAX",
         *          "taxSum":0
         *      }],
         *      "payments":[{
         *          "sum":123,
         *          "type":"CASHLESS"
         *      }],
         *      "attributes":{
         *          "email":null,
         *          "phone":"+79001234567"
         *      },
         *      "total":{
         *          "priceSum":123
         *      }
         *  }
         */
        assertEquals("mdOrder", rcpt.externalId());
        assertEquals(123, rcpt.deviceId());
        assertEquals(OperationType.SALE, rcpt.type());
        assertEquals(TaxMode.SIMPLE_WO, rcpt.taxMode());
        assertEquals(PositionType.SERVICE, rcpt.positions().get(0).type());
        assertEquals(VatType.NDS_NO_TAX, rcpt.positions().get(0).tax());
        assertEquals(PaymentType.CASHLESS, rcpt.payments().get(0).type());
        assertEquals(123, rcpt.payments().get(0).sum());
        assertEquals(123, rcpt.total().priceSum());
    }

    @Test
    void testReceiptMapper() throws StreamWriteException, DatabindException, IOException {
        var order = new ReceiptOrder(1.23, "orderNumber", "123456", "mdOrder", new ReceiptCustomerInfo(null, "79001234567"));
        var rcpt = ReceiptBuilder.createReceipt(order, 123);
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.writeValueAsString(rcpt);
        assertTrue(json.contains("\"type\":\"SALE\""));
    }
}
