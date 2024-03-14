package ru.openfs.lbpay.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.DreamkasClient;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.dto.dreamkas.type.OperationStatus;
import ru.openfs.lbpay.mapper.ReceiptOrderBuilder;

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
        var rcpt = ReceiptOrderBuilder.createReceiptOrder("1", "1", 0.0, "1", "1@data.com", null);
        service.registerReceipt(rcpt);
    }

}
