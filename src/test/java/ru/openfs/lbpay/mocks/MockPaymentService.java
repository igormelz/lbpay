package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.service.PaymentService;

import java.time.Instant;

@Mock
@ApplicationScoped
public class MockPaymentService implements PaymentService {

    @Override
    public void processPayment(Long orderNumber, String mdOrder, Instant payDate) {}

    @Override
    public void processDecline(long orderNumber, String mdOrder) {}

}