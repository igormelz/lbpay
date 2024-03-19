package ru.openfs.lbpay.resource;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.mocks.MockReceiptService;

@QuarkusTest
class DreamkasWebhookResourceTest {

    @Inject
    MockReceiptService receiptService;

    // @Test
    // void testCallbackCreateReceipt() {
    //     given()
    //             .contentType("application/json")
    //             .body(new Webhook(WebhookAction.CREATE, WebhookType.RECEIPT, JsonObject.of()))
    //             .post("/pay/dreamkas")
    //             .then()
    //             .statusCode(204);
    // }

    @Test
    void testCallbackCreateOperation() {
        var json = """
                    {
                        "action" : "UPDATE",
                        "type" : "OPERATION",
                        "data" : {
                          "externalId" : "2d77e92e-000f-5000-a000-157c199a9450",
                          "createdAt" : "2021-09-08T12:35:21.744293",
                          "id" : "217d085f66b84f8c9de6ab18593807e6",
                          "status" : "IN_PROGRESS"
                        }
                      }
                """;
        given()
                .contentType("application/json")
                .body(json)
                .post("/pay/dreamkas")
                .then()
                .statusCode(204);
    }

    // @Test
    // void testCallbackUnhandled() {
    //     given()
    //             .contentType("application/json")
    //             .body(new Webhook(WebhookAction.CREATE, WebhookType.DEVICE, JsonObject.of()))
    //             .post("/pay/dreamkas")
    //             .then()
    //             .statusCode(204);
    // }
}
