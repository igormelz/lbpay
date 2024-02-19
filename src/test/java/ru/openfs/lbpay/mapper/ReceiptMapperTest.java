package ru.openfs.lbpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;

class ReceiptMapperTest {
    @Test
    void testIsValid() {
        var ok = new ReceiptCustomerInfo("a@b.com", "79001234567");
        assertEquals(true, ReceiptMapper.isValid(ok));
        assertEquals("+79001234567", ok.phone());

        var phone = new ReceiptCustomerInfo(null, "+79001234567");
        assertEquals(true, ReceiptMapper.isValid(phone));
        assertEquals("+79001234567", phone.phone());

        var email = new ReceiptCustomerInfo("1@a.com", null);
        assertEquals(true, ReceiptMapper.isValid(email));
        assertEquals("1@a.com", email.email());

        var empty = new ReceiptCustomerInfo(null, null);
        assertEquals(false, ReceiptMapper.isValid(empty));

        var badPhone = new ReceiptCustomerInfo(null, "12345");
        assertEquals(false, ReceiptMapper.isValid(badPhone));

        var badEmail = new ReceiptCustomerInfo("1@1", null);
        assertEquals(false, ReceiptMapper.isValid(badEmail));
    }

    @Test
    void testCreateReceipt() {
        var order = new ReceiptOrder(1.23, "orderNumber", "123456", "mdOrder", new ReceiptCustomerInfo(null, "79001234567"));
        var rcpt = ReceiptMapper.createReceipt(order, 123);
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
        assertEquals("SALE", rcpt.type());
        assertEquals("SIMPLE_WO", rcpt.taxMode());
        assertEquals("SERVICE", rcpt.positions().get(0).type());
        assertEquals("NDS_NO_TAX", rcpt.positions().get(0).tax());
        assertEquals(123, rcpt.total().priceSum());
    }
}
