package ru.openfs.lbpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.dreamkas.mapper.ReceiptBuilder;
import ru.openfs.lbpay.client.dreamkas.model.type.OperationType;
import ru.openfs.lbpay.client.dreamkas.model.type.PaymentType;
import ru.openfs.lbpay.client.dreamkas.model.type.PositionType;
import ru.openfs.lbpay.client.dreamkas.model.type.TaxMode;
import ru.openfs.lbpay.client.dreamkas.model.type.VatType;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;

@QuarkusTest
class ReceiptBuilderTest {

    @Inject 
    ReceiptBuilder receiptBuilder;

    @Test
    void testIsValid() {
        var addPrefix = new ReceiptCustomer("a@b.com", "79001234567");
        assertTrue(receiptBuilder.isValid(addPrefix));
        assertTrue(addPrefix.phone().startsWith("+"));
        assertTrue(receiptBuilder.isValid(new ReceiptCustomer("", "+79001234567")));
        assertTrue(receiptBuilder.isValid(new ReceiptCustomer("1@a.com", "")));

        assertFalse(receiptBuilder.isValid(new ReceiptCustomer("", "")));
        assertFalse(receiptBuilder.isValid(new ReceiptCustomer(null, null)));
        assertFalse(receiptBuilder.isValid(new ReceiptCustomer(null, "12345")));
        assertFalse(receiptBuilder.isValid(new ReceiptCustomer("1@1", null)));
    }

    @Test
    void testCreateReceiptWithDefaultValues() {
        var order = new ReceiptOrder(1.23, "orderNumber", "123456", "mdOrder", new ReceiptCustomer(null, "79001234567"));
        var rcpt = receiptBuilder.createReceipt(order);
        assertEquals("mdOrder", rcpt.externalId());

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
        var order = new ReceiptOrder(1.23, "orderNumber", "123456", "mdOrder", new ReceiptCustomer(null, "79001234567"));
        var rcpt = receiptBuilder.createReceipt(order);
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.writeValueAsString(rcpt);
        assertTrue(json.contains("\"type\":\"SALE\""));
    }
}
