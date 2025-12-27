package ru.openfs.lbpay.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import ru.openfs.lbpay.client.dreamkas.DreamkasClient;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.dreamkas.Operation;
import ru.openfs.lbpay.model.dreamkas.Receipt;
import ru.openfs.lbpay.model.dreamkas.type.*;
import ru.openfs.lbpay.utils.NdsCalculator;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class ReceiptServiceTest {

    @InjectMock
    @RestClient
    DreamkasClient mock;

    @Inject
    ReceiptService service;

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
    void testCreateReceiptWithNds5() {
        var rcpt = Receipt.createNds5("mdOrder", 1, 123, null, null, "Test");
        assertEquals("mdOrder", rcpt.externalId());
        assertEquals(OperationType.SALE, rcpt.type());
        assertEquals(TaxMode.SIMPLE_WO, rcpt.taxMode());
        assertEquals(PositionType.SERVICE, rcpt.positions().getFirst().type());
        assertEquals("Test", rcpt.positions().getFirst().name());
        assertEquals(VatType.NDS_5, rcpt.positions().getFirst().tax());
        assertEquals(6, rcpt.positions().getFirst().taxSum());
        assertEquals(PaymentType.CASHLESS, rcpt.payments().getFirst().type());
        assertEquals(123, rcpt.payments().getFirst().sum());
        assertEquals(123, rcpt.total().priceSum());
    }

    @Test
    void testRegisterReceipt() {
        when(mock.register(any())).thenReturn(new Operation("1", "1", null, OperationStatus.PENDING, null, null, null));
        var rcpt = new ReceiptOrder(
                0.0, "1", "1", "1", new ReceiptCustomer("1@data.com", null),
                NdsCalculator.needNds(Instant.now()));
        service.registerReceipt(rcpt);
    }

}
