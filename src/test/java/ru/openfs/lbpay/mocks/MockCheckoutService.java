package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.service.CheckoutService;

@Mock
@ApplicationScoped
public class MockCheckoutService implements CheckoutService {

    @Override
    public boolean isActiveAccount(String account) {
        return true;
    }

    @Override
    public String processCheckout(String account, Double amount) {
        return "http://payment_url";
    }
    
}