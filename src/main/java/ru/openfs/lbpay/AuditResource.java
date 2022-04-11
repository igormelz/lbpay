package ru.openfs.lbpay;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.core.eventbus.Message;
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
                        Log.error(String.format("!!! re-processing order:%s not found", key));
                        throw new NotFoundException("order not found");
                    } else if (order.operId == null) {
                        Log.info(String.format("--> re-processing orderNumber: %s with status: not registered",
                                order.orderNumber));
                        bus.send("receipt-sale", JsonObject.mapFrom(order));
                        return "submit re-processing not registered order";
                    } else if (order.status.equalsIgnoreCase("ERROR")) {
                        Log.warn(String.format("--> re-processing orderNumber: %s with status: error",
                                order.orderNumber));
                        bus.send("receipt-sale", JsonObject.mapFrom(order));
                        return "submit re-processing error order";
                    } else {
                        Log.info("--> ask operation status");
                        return "ask oper status";
                    }
                });
    }

    @GET
    @Path("pending")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<JsonObject> pending() {
        return audit.getPending();
    }

    @GET
    @Path("pending/{orderNumber}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<JsonObject> pendingStatus(@PathParam("orderNumber") long orderNumber) {
        return bus.<JsonObject>request("sber-order-status", orderNumber)
                .onItem().transform(Message::body);
    }

}
