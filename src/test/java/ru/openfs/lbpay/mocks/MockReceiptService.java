package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.service.ReceiptService;

@Mock
@ApplicationScoped
public class MockReceiptService implements ReceiptService {
    @Override
    public void registerReceipt(ReceiptOrder receiptOrder) {}
}
