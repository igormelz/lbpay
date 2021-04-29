package ru.openfs.dreamkas;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.ext.web.client.predicate.ErrorConverter;
import io.vertx.mutiny.ext.web.client.predicate.ResponsePredicate;
import ru.openfs.audit.AuditRepository;
import ru.openfs.dreamkas.model.Receipt;

@Path("/pay/dreamkas")
public class DreamkasResource {
    private static final Logger LOG = LoggerFactory.getLogger(DreamkasResource.class);
    private static final String SERVICE_NAME = "Оплата услуг";
    private WebClient client;

    @ConfigProperty(name = "dreamkas.host", defaultValue = "kabinet.dreamkas.ru")
    String host;

    @ConfigProperty(name = "dreamkas.port", defaultValue = "443")
    int port;

    @ConfigProperty(name = "dreamkas.ssl", defaultValue = "true")
    Boolean useSsl;

    @ConfigProperty(name = "dreamkas.token", defaultValue = "auth-token")
    String token;

    @ConfigProperty(name = "dreamkas.deviceId", defaultValue = "99999")
    int deviceId;

    @Inject
    AuditRepository service;

    @Inject
    Vertx vertx;

    @PostConstruct
    void initialize() {
        this.client = WebClient.create(vertx,
                new WebClientOptions().setDefaultHost(host).setDefaultPort(port).setIdleTimeout(3).setSsl(useSsl));
    }

    ErrorConverter converter = ErrorConverter.createFullBody(result -> {
        HttpResponse<Buffer> response = result.response();
        String err = response.bodyAsString();
        if (err != null && !err.isBlank()) {
            LOG.error(err);
            return new RuntimeException(err);
        }
        LOG.error(result.message());
        return new RuntimeException(result.message());
    });

    ResponsePredicate predicate = ResponsePredicate.create(ResponsePredicate.SC_SUCCESS, converter);

    @ConsumeEvent("receipt-sale")
    public void receiptSale(JsonObject message) {
        boolean isValid = false;

        if (message.containsKey("email") && message.getString("email")
                .matches("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$")) {
            isValid = true;
        } else if (message.containsKey("phone") && message.getString("phone").matches("^\\+?[1-9]\\d{10,13}+$")) {
            isValid = true;
            if (!message.getString("phone").startsWith("+"))
                message.put("phone", "+" + message.getString("phone"));
        }

        if (!isValid) {
            LOG.error("!!! receipt orderNumber: {} - no required email: {} or phone: {} in the account:{}",
                    message.getString("orderNumber"), message.getString("email"), message.getString("phone"),
                    message.getString("account"));
            return;
        }

        // create checkpoint
        service.setOrder(message);

        LOG.info("<-- receipt orderNumber: {}", message.getString("orderNumber"));
        client.post("/api/receipts").expect(predicate).putHeader("Authorization", "Bearer " + token)
                .sendJson(buildReceipt(message)).subscribe().with(response -> {
                    JsonObject operation = response.bodyAsJsonObject();
                    LOG.info("--> {} receipt orderNumber: {}, operation: {}",
                            operation.getString("status").toLowerCase(), message.getString("orderNumber"),
                            operation.getString("id"));
                    // update checkpoint
                    service.setOperation(operation);
                }, err -> {
                    LOG.error("!!! receipt orderNumber: {} - {}", message.getString("orderNumber"), err.getMessage());
                });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void webhook(JsonObject message) {
        JsonObject data = message.getJsonObject("data");
        if (!data.containsKey("externalId")) {
            LOG.warn(data.encodePrettily());
            return;
        }

        JsonObject order = service.getOrder(data.getString("externalId"));
        if (!order.isEmpty()) {
            if (message.getString("type").equalsIgnoreCase("OPERATION")) {
                if (data.getString("status").equalsIgnoreCase("ERROR")) {
                    LOG.error("!!! receipt orderNumber: {} - {}", order.getString("orderNumber"),
                            data.getJsonObject("data").getJsonObject("error").getString("message"));
                } else {
                    LOG.info("--> {} receipt orderNumber: {}, operation: {}", data.getString("status").toLowerCase(),
                            order.getString("orderNumber"), data.getString("id"));
                }
                service.setOperation(data);
            }

            if (message.getString("type").equalsIgnoreCase("RECEIPT")) {
                LOG.info("--> ofd receipt orderNumber: {}, shift: {}, doc: {}", order.getString("orderNumber"),
                        message.getJsonObject("data").getLong("shiftId"),
                        message.getJsonObject("data").getValue("fiscalDocumentNumber", "fiscalDocumentNumber"));
            }
        }
    }

    @GET
    @Path("orders")
    public List<String> getOrders() {
        return service.orders();
    }

    @GET
    @Path("operation/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject operation(@PathParam("key") String key) {
        return service.getOperation(key);
    }

    @GET
    @Path("orders/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject order(@PathParam("key") String key) {
        return service.getOrder(key);
    }

    @PUT
    @Path("orders/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    public String receiptOrder(@PathParam("key") String key) {
        JsonObject order = service.getOrder(key);
        if (order.isEmpty()) {
            LOG.error("!!! re-processing order:{} not found", key);
            throw new NotFoundException("order not found");
        }
        JsonObject oper = service.getOperation(key);
        if (oper.isEmpty()) {
            LOG.info("--> re-processing orderNumber:{} not registered yet", order.getString("orderNumber"));
            receiptSale(order);
            return "re-processing not registered order";
        } else if (oper.getString("status").equalsIgnoreCase("ERROR")) {
            LOG.warn("--> re-processing orderNumber:{} on error:{}", order.getString("orderNumber"),
                    oper.getJsonObject("data").getJsonObject("error").getString("message"));
            receiptSale(order);
            return "re-processing error order";
        } else {
            // get dk operation status
            return client.get("/api/operations/" + oper.getString("id")).expect(predicate)
                    .putHeader("Authorization", "Bearer " + token).send().await().indefinitely().bodyAsJsonObject()
                    .getString("status");
        }
    }

    private Receipt buildReceipt(JsonObject message) {
        // calc service price
        long price = (long) (message.getDouble("amount") * 100);

        // build new Receipt
        Receipt receipt = new Receipt();
        receipt.deviceId = deviceId;
        receipt.externalId = message.getString("mdOrder");

        // add attributes
        receipt.attributes = new Receipt.Attributes();
        if (message.containsKey("email")) {
            receipt.attributes.email = message.getString("email");
        }
        if (message.containsKey("phone")) {
            receipt.attributes.phone = message.getString("phone");
        }

        // add position
        var position = new Receipt.Position();
        position.name = SERVICE_NAME;
        position.price = price;
        position.priceSum = price;
        receipt.positions = List.of(position);

        // add payment
        var payment = new Receipt.Payment();
        payment.sum = price;
        receipt.payments = List.of(payment);

        // add total
        receipt.total = new Receipt.Total();
        receipt.total.priceSum = price;

        return receipt;
    }
}
