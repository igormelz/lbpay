package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineMessage;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineRequest;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineResponseType;
import ru.openfs.lbpay.service.SberOnlineService;

@Mock
@ApplicationScoped
public class MockSberOnlineService extends SberOnlineService {

    @Override
    public SberOnlineMessage processCheckAccount(String account) {
        return new SberOnlineMessage.Builder().responseType(SberOnlineResponseType.OK)
                .setBalance(1.0).setRecSum(10.0)
                .setAddress("SPB").build();
    }

    @Override
    public SberOnlineMessage processPayment(SberOnlineRequest request) {
        return new SberOnlineMessage.Builder()
                .responseType(SberOnlineResponseType.OK)
                .setExtId(101010L)
                .setSum(request.amount())
                .setRegDate("2024-01-01 12:32:00")
                .build();
    }
}
