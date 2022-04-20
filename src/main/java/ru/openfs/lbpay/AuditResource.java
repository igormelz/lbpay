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
package ru.openfs.lbpay;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import ru.openfs.lbpay.model.AuditOrder;
import ru.openfs.lbpay.model.AuditRecord;

@Path("/audit")
public class AuditResource {

    @Inject
    AuditRepository audit;

    @Inject
    EventBus bus;

    @GET
    @Path("order")
    public Multi<AuditRecord> getOrders() {
        return audit.findAll();
    }

    @GET
    @Path("order/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<AuditRecord> order(@PathParam("key") String key) {
        return audit.findById(key);
    }

    @PUT
    @Path("order/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> receiptOrder(@PathParam("key") String key) {
        return audit.findById(key).onItem()
                .transform(order -> {
                    if (order == null) {
                        Log.errorf("re-processing order:%s not found", key);
                        throw new NotFoundException("order not found");

                    } else if (order.operId == null) {
                        Log.infof("re-processing not registered orderNumber: %s", order.orderNumber);
                        bus.send("receipt-sale", JsonObject.mapFrom(order));
                        return "submit re-processing not registered order";

                    } else if (order.status.equalsIgnoreCase("ERROR")) {
                        Log.warnf("re-processing error orderNumber: %s", order.orderNumber);
                        bus.send("receipt-sale", JsonObject.mapFrom(order));
                        return "submit re-processing error order";
                        
                    } else {
                        Log.info("need to ask operation status");
                        return "ask oper status";
                    }
                });
    }

    @GET
    @Path("pending")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<AuditOrder> pending() {
        return audit.getPendingOrders();
    }

    @PUT
    @Path("cancel/{orderNumber}")
    public Uni<Response> clearPendingOrder(@PathParam("orderNumber") long orderNumber) {
        return audit.clearOrder(orderNumber).onItem()
                .transform(cleared -> {
                    if (cleared) {
                        Log.infof("orderNumber: %d cancelled", orderNumber);
                        return Response.ok().build();
                    }
                    return Response.notModified().build();
                });
    }

}
