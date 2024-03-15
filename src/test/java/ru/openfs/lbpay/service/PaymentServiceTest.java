package ru.openfs.lbpay.service;

import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;

@QuarkusTest
class PaymentServiceTest {

    @InjectMock
    @MockitoConfig(convertScopes = true)
    LbCoreSoapClient lbClient;

    @Inject
    PaymentService paymentService;

    @Test
    void testProcessPayment() {
        when(lbClient.login()).thenReturn(null);
        when(lbClient.findOrderNumber("sessionId", 1L)).thenReturn(Optional.empty());
        paymentService.processPayment(1L, "1");
    }
    
}
