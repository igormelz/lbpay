package ru.openfs.lbpay.resource.webhook;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.openfs.lbpay.client.yookassa.model.Webhook;
import ru.openfs.lbpay.service.PaymentService;

@Path("/pay/yookassa")
public class YookassaWebhookResource {

    @Inject
    PaymentService paymentService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response callback(Webhook webhook) {
        Log.debug(webhook);
        if (webhook.event().equalsIgnoreCase("payment.succeeded")) {
            paymentService.processPayment(Long.parseLong(webhook.object().metadata().paymentId()), webhook.object().id());
        } else if (webhook.event().equalsIgnoreCase("payment.canceled")) {
            paymentService.processDecline(Long.parseLong(webhook.object().metadata().paymentId()));
        } else {
            Log.warn("unprocessed event:" + webhook.event());
        }
        return Response.ok().build();
    }
}
