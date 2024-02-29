package ru.openfs.lbpay.resource;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.dto.dreamkas.Webhook;
import ru.openfs.lbpay.dto.dreamkas.type.WebhookAction;
import ru.openfs.lbpay.dto.dreamkas.type.WebhookType;
import ru.openfs.lbpay.service.ReceiptService;

@QuarkusTest
class DreamkasWebhookResourceTest {

    @Inject
    MockReceiptService receiptService;

    @Test
    void testCallbackCreateReceipt() {
        given()
                .contentType("application/json")
                .body(new Webhook(WebhookAction.CREATE, WebhookType.RECEIPT, JsonObject.of()))
                .post("/pay/dreamkas")
                .then()
                .statusCode(204);
    }

    @Test
    void testCallbackCreateOperation() {
        given()
                .contentType("application/json")
                .body(new Webhook(WebhookAction.CREATE, WebhookType.OPERATION, JsonObject.of()))
                .post("/pay/dreamkas")
                .then()
                .statusCode(204);
    }

    @Test
    void testCallbackUnhandled() {
        given()
                .contentType("application/json")
                .body(new Webhook(WebhookAction.CREATE, WebhookType.DEVICE, JsonObject.of()))
                .post("/pay/dreamkas")
                .then()
                .statusCode(204);
    }

    @Mock
    @ApplicationScoped
    public static class MockReceiptService extends ReceiptService {
        @Override
        public void processReceiptOperation(Operation operation) {}
    }
}
