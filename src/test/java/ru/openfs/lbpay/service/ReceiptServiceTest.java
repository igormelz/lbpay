package ru.openfs.lbpay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.dreamkas.DreamkasClient;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.dreamkas.Operation;
import ru.openfs.lbpay.model.dreamkas.Receipt;
import ru.openfs.lbpay.model.dreamkas.type.OperationStatus;
import ru.openfs.lbpay.model.dreamkas.type.OperationType;
import ru.openfs.lbpay.model.dreamkas.type.PaymentType;
import ru.openfs.lbpay.model.dreamkas.type.PositionType;
import ru.openfs.lbpay.model.dreamkas.type.TaxMode;
import ru.openfs.lbpay.model.dreamkas.type.VatType;

@QuarkusTest
class ReceiptServiceTest {

    @InjectMock
    @RestClient
    DreamkasClient mock;

    @Inject
    ReceiptService service;

    // @Test
    // void testIsValid() {
    //     var addPrefix = new ReceiptCustomer("a@b.com", "79001234567");
    //     assertTrue(receiptBuilder.isValid(addPrefix));
        
    //     assertTrue(addPrefix.phone().startsWith("+"));
    //     assertTrue(receiptBuilder.isValid(new ReceiptCustomer("", "+79001234567")));
    //     assertTrue(receiptBuilder.isValid(new ReceiptCustomer("1@a.com", "")));

    //     assertFalse(receiptBuilder.isValid(new ReceiptCustomer("", "")));
    //     assertFalse(receiptBuilder.isValid(new ReceiptCustomer(null, null)));
    //     assertFalse(receiptBuilder.isValid(new ReceiptCustomer(null, "12345")));
    //     assertFalse(receiptBuilder.isValid(new ReceiptCustomer("1@1", null)));
    // }

    @Test
    void testCreateReceiptWithDefaultValues() {
        var rcpt = new Receipt("mdOrder", 1, 123, null, null, "Test");
        assertEquals("mdOrder", rcpt.externalId());
        assertEquals(OperationType.SALE, rcpt.type());
        assertEquals(TaxMode.SIMPLE_WO, rcpt.taxMode());
        assertEquals(PositionType.SERVICE, rcpt.positions().getFirst().type());
        assertEquals("Test", rcpt.positions().getFirst().name());
        assertEquals(VatType.NDS_NO_TAX, rcpt.positions().getFirst().tax());
        assertEquals(PaymentType.CASHLESS, rcpt.payments().getFirst().type());
        assertEquals(123, rcpt.payments().getFirst().sum());
        assertEquals(123, rcpt.total().priceSum());
    }

    @Test
    void testRegisterReceipt() {
        when(mock.register(any())).thenReturn(new Operation("1", "1", null, OperationStatus.PENDING, null, null, null));
        var rcpt = new ReceiptOrder(
                0.0, "1", "1", "1", new ReceiptCustomer("1@data.com", null));
        service.registerReceipt(rcpt);
    }

}
