package ru.openfs.lbpay.resource;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.dto.yookassa.MetaData;
import ru.openfs.lbpay.dto.yookassa.Payment;
import ru.openfs.lbpay.dto.yookassa.Webhook;
import ru.openfs.lbpay.resource.SberWebhookResourceTest.MockPaymentService;

@QuarkusTest
class YookassaWebhookResourceTest {

    @Inject
    MockPaymentService paymentService;

    @Test
    void testCallbackSuccess() {
        given()
                .contentType("application/json")
                .body(new Webhook(
                        "notification", "payment.succeeded",
                        new Payment(
                                "123456", "succeed", null, null, null,
                                null, null, null, null,
                                null, null, new MetaData("123456"), null, null, null)))
                .post("/pay/yookassa")
                .then()
                .statusCode(200);
    }

    @Test
    void testCallbackCanceled() {
        given()
                .contentType("application/json")
                .body(new Webhook(
                        "notification", "payment.canceled",
                        new Payment(
                                "123456", "??", null, null, null,
                                null, null, null, null,
                                null, null, new MetaData("123456"), null, null, null)))
                .post("/pay/yookassa")
                .then()
                .statusCode(200);
    }


}
