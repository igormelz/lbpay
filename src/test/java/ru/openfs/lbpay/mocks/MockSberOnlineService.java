package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.model.sberonline.SberOnlineResponse;
import ru.openfs.lbpay.model.sberonline.SberOnlineResponseType;
import ru.openfs.lbpay.service.SberOnlineService;

@Mock
@ApplicationScoped
public class MockSberOnlineService extends SberOnlineService {

    @Override
    public SberOnlineResponse processCheckAccount(String account) {
        return new SberOnlineResponse.Builder().responseType(SberOnlineResponseType.OK)
                .setBalance(1.0).setRecSum(10.0)
                .setAddress("SPB").build();
    }

    @Override
    public SberOnlineResponse processPayment(String account, String payId, Double amount, String payDate) {
        return new SberOnlineResponse.Builder()
                .responseType(SberOnlineResponseType.OK)
                .setExtId(101010L)
                .setSum(amount)
                .setRegDate("2024-01-01 12:32:00")
                .build();
    }
}
