package ru.openfs.lbpay.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.dreamkas.DreamkasClient;
import ru.openfs.lbpay.client.dreamkas.model.Operation;
import ru.openfs.lbpay.client.dreamkas.model.type.OperationStatus;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;

@QuarkusTest
class ReceiptServiceTest {

    @InjectMock
    @RestClient
    DreamkasClient mock;

    @Inject
    ReceiptService service;

    @Test
    void testRegisterReceipt() {
        when(mock.register(any())).thenReturn(new Operation("1", "1", null, OperationStatus.PENDING, null, null, null));
        var rcpt = new ReceiptOrder(
                0.0, "1", "1", "1", new ReceiptCustomer("1@data.com", null));
        service.registerReceipt(rcpt);
    }

}
