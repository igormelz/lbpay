package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.model.SberOnlineCheckResponse;
import ru.openfs.lbpay.model.SberOnlinePaymentResponse;
import ru.openfs.lbpay.model.SberOnlineRequest;
import ru.openfs.lbpay.service.SberOnlineService;

@Mock
@ApplicationScoped
public class MockSberOnlineService extends SberOnlineService {
    @Override
    public SberOnlineCheckResponse processCheckAccount(String account) {
        return new SberOnlineCheckResponse(1.0, 10.0, "SPB");
    }

    @Override
    public SberOnlinePaymentResponse processPayment(SberOnlineRequest request) {
        return new SberOnlinePaymentResponse(101010L, request.amount(), "2024-01-01 12:32:00", null);
    }
}