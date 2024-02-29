/*
 * Copyright 2021,2022 OpenFS.RU
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
package ru.openfs.lbpay.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.logging.Log;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.dto.dreamkas.Webhook;
import ru.openfs.lbpay.service.ReceiptService;

@Path("/pay/dreamkas")
public class DreamkasWebhookResource {

    @Inject
    ReceiptService receiptService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void callback(Webhook webhook) {
        Log.debug("start processing webhook:" + webhook);

        switch (webhook.type()) {

            case RECEIPT -> Log.infof("OFD shift: %d, doc: %s",
                    webhook.data().getLong("shiftId"),
                    webhook.data().getString("fiscalDocumentNumber"));

            case OPERATION -> {
                try {
                    var operation = webhook.data().mapTo(Operation.class);
                    receiptService.processReceiptOperation(operation);
                } catch (IllegalArgumentException e) {
                    Log.error("mapping webhook data to operation:" + e.getMessage());
                }
            }

            default -> Log.info("unhandled webhook type:" + webhook.type());
        }
    }

}
