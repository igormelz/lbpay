package ru.openfs.lbpay.client.yookassa;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import ru.openfs.lbpay.client.yookassa.model.Payment;
import ru.openfs.lbpay.client.yookassa.model.PaymentRequest;

@RegisterRestClient(configKey = "YooKassa")
@RegisterClientHeaders(YookassaHeaderFactory.class)
public interface YookassaClient {
    @POST
    @Path("/v3/payments")
    Payment payments(PaymentRequest request);
}
