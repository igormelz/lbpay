/*
 * Copyright 2021-2024 OpenFS.RU
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.openfs.lbpay.resource.webhook;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.openfs.lbpay.model.yookassa.Webhook;
import ru.openfs.lbpay.service.PaymentService;

@Path("/pay/yookassa")
public class YookassaResource {

    @Inject
    PaymentService paymentService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RunOnVirtualThread
    public Response callback(Webhook webhook) {
        Log.debug(webhook);
        if (webhook.event().equalsIgnoreCase("payment.succeeded")) {
            paymentService.processPayment(Long.parseLong(webhook.object().metadata().paymentId()), webhook.object().id());
        } else if (webhook.event().equalsIgnoreCase("payment.canceled")) {
            paymentService.processDecline(Long.parseLong(webhook.object().metadata().paymentId()), webhook.object().id());
        } else {
            Log.warn("unprocessed event:" + webhook.event());
        }
        return Response.ok().build();
    }
}
