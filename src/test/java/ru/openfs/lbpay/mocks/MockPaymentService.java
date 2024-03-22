package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.service.PaymentService;

@Mock
@ApplicationScoped
public class MockPaymentService implements PaymentService {

    @Override
    public void processPayment(Long orderNumber, String mdOrder) {}

    @Override
    public void processDecline(long orderNumber) {}
}