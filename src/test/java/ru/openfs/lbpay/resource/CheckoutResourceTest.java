package ru.openfs.lbpay.resource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import ru.openfs.lbpay.mocks.MockCheckoutService;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class CheckoutResourceTest {

    @Inject
    MockCheckoutService checkoutService;

    @Test
    void testCheckAccountOk() {
        given()
                .param("uid", "111111")
                .when().get("/pay/checkout")
                .then()
                .statusCode(204);
    }

    @Test
    void testCheckAccountFailPattern() {
        given()
                .param("uid", "111")
                .when().get("/pay/checkout")
                .then()
                .statusCode(400);
    }

    @Test
    void testCheckoutFailPattern() {
        given()
                .urlEncodingEnabled(true)
                .param("uid", "000").and()
                .param("amount", 10.01)
                .when().post("/pay/checkout")
                .then()
                .statusCode(400);
    }

    @Test
    void testCheckoutLessAmount() {
        given()
                .urlEncodingEnabled(true)
                .param("uid", "111111").and()
                .param("amount", 1.01)
                .when().post("/pay/checkout")
                .then()
                .statusCode(400);
    }

    @Test
    void testCheckoutHugeAmount() {
        given()
                .urlEncodingEnabled(true)
                .param("uid", "111111").and()
                .param("amount", 20001.01)
                .when().post("/pay/checkout")
                .then()
                .statusCode(400);
    }

    @Test
    void testCheckoutOk() {
        given()
                .redirects().follow(false)
                .urlEncodingEnabled(true)
                .param("uid", "111111").and()
                .param("amount", 10.01)
                .when().post("/pay/checkout")
                .then()
                .statusCode(303)
                .header("Location", containsString("payment_url"));
    }

}
