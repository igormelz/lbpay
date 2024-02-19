package ru.openfs.lbpay.resource;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import ru.openfs.lbpay.dto.yookassa.YookassaWebhook;
import ru.openfs.lbpay.service.PaymentService;

@Path("/pay/yookassa")
public class YookassaWebhookResource {
    
    @Inject
    PaymentService paymentService;

    @POST
    public Response callback(YookassaWebhook webhook) {
        Log.debug(webhook);
        if(webhook.event().equalsIgnoreCase("payment.succeeded")) {
            var orderNumber = Long.parseLong(webhook.object().metadata().paymentId());
            var mdOrder = webhook.object().id();
            paymentService.processPayment(orderNumber, mdOrder);
        }
        return Response.ok().build();
    }
}
