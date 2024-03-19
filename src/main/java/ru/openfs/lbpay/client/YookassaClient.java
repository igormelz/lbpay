package ru.openfs.lbpay.client;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import ru.openfs.lbpay.dto.yookassa.PaymentRequest;
import ru.openfs.lbpay.dto.yookassa.Payment;

@RegisterRestClient(configKey = "YooKassa")
@RegisterClientHeaders(YookassaHeaderFactory.class)
public interface YookassaClient {
    @POST
    @Path("/v3/payments")
    Payment payments(PaymentRequest request);
}
