package ru.openfs.lbpay.resource;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.service.PaymentService;

@QuarkusTest
class SberWebhookResourceTest {

    @Inject
    MockPaymentService paymentService;

    @Test
    void testCallbackDepositedSuccess() {
        given().urlEncodingEnabled(true)
                .param("mdOrder", "123456")
                .param("operation", "deposited")
                .param("orderNumber", "123456")
                .param("status", "1")
                .get("/pay/sber/callback")
                .then()
                .statusCode(200);
    }

    @Test
    void testCallbackDepositedUnsuccess() {
        given().urlEncodingEnabled(true)
                .param("mdOrder", "123456")
                .param("operation", "deposited")
                .param("orderNumber", "123456")
                .param("status", "0")
                .get("/pay/sber/callback")
                .then()
                .statusCode(200);
    }

    @Test
    void testCallbackDeclined() {
        given().urlEncodingEnabled(true)
                .param("mdOrder", "123456")
                .param("operation", "declinedByTimeout")
                .param("orderNumber", "123456")
                .param("status", "0")
                .get("/pay/sber/callback")
                .then()
                .statusCode(200);
    }

    @Test
    void testCallbackFail() {
        given().urlEncodingEnabled(true)
                .param("mdOrder", "123456")
                .param("operation", "dddd")
                .param("orderNumber", "123456")
                .param("status", "1")
                .get("/pay/sber/callback")
                .then()
                .statusCode(500);
    }

    @Mock
    @ApplicationScoped
    public static class MockPaymentService extends PaymentService {

        @Override
        public void processPayment(Long orderNumber, String mdOrder) {}

        @Override
        public void processDecline(long orderNumber) {}
    }

}
