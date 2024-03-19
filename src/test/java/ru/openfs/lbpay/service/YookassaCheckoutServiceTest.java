package ru.openfs.lbpay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.YookassaClient;
import ru.openfs.lbpay.dto.yookassa.Confirmation;
import ru.openfs.lbpay.dto.yookassa.Payment;
import ru.openfs.lbpay.exception.CheckoutException;

@QuarkusTest
class YookassaCheckoutServiceTest {

    @InjectMock
    @RestClient
    YookassaClient mock;

    @Inject
    YookassaCheckoutService service;

    @Test
    void testCreatePayment() {
        
        when(mock.payments(any())).thenReturn(new Payment("1", "pending", null, null, 
        null, null, null, null, null, 
        null, new Confirmation("redirect", null, "payment_url"), null, null, null, null));

        var response = service.createPayment(123456L, "123456", 11.11);
        assertEquals("payment_url", response);
    }

    @Test
    void testCreatePaymentException() {
        
        when(mock.payments(any())).thenReturn(new Payment("1", "pending", null, null, 
        null, null, null, null, null, 
        null, null, null, null, null, null));

        var ex = assertThrows(CheckoutException.class, () -> service.createPayment(123456L, "123456", 11.11));
        assertEquals("no confiramtion url", ex.getMessage());
    }
}
