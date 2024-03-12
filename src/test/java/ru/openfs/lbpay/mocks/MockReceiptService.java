package ru.openfs.lbpay.mocks;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.service.ReceiptService;

@Mock
@ApplicationScoped
public class MockReceiptService extends ReceiptService {
    @Override
    public void processReceiptOperation(Operation operation) {}
}
