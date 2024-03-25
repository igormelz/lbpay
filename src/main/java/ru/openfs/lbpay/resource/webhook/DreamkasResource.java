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

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.core.json.JsonObject;
import ru.openfs.lbpay.model.dreamkas.Operation;
import ru.openfs.lbpay.service.ReceiptOperation;

@Path("/pay/dreamkas")
public class DreamkasResource {

    @Inject
    ReceiptOperation receiptOperation;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RunOnVirtualThread
    public void callback(JsonObject webhook) {
        Log.debug("start processing webhook:" + webhook);

        switch (webhook.getString("type")) {

            case "RECEIPT" -> Log.infof("OFD shift: %d, doc: %s",
                    webhook.getJsonObject("data").getLong("shiftId"),
                    webhook.getJsonObject("data").getString("fiscalDocumentNumber"));

            case "OPERATION" -> {
                try {
                    var operation = webhook.getJsonObject("data").mapTo(Operation.class);
                    Log.debug(operation);
                    receiptOperation.processOperation(operation);
                } catch (IllegalArgumentException e) {
                    Log.error("mapping webhook data to operation:" + e.getMessage());
                }
            }

            default -> Log.info("unhandled webhook type:" + webhook.getString("type"));
        }
    }

}
