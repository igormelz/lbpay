package ru.openfs.lbpay.resource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
//import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class CheckoutResourceTest {

    @Test
    void testCheckAccountOk() {
        given()
                .param("uid", "111111")
                .when().get("/pay/checkout")
                .then()
                .statusCode(204);
    }

    @Test
    void testCheckAccountFailUnknown() {
        given()
                .param("uid", "000000")
                .when().get("/pay/checkout")
                .then()
                .statusCode(400);
    }

    @Test
    void testCheckAccountOkFailPattern() {
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
    void testCheckoutInactive() {
        given()
                .urlEncodingEnabled(true)
                .param("uid", "111112").and()
                .param("amount", 10.01)
                .when().post("/pay/checkout")
                .then()
                .statusCode(400);
    }

    // @Test
    // public void testCheckoutOk() {
    //     given()
    //             .redirects().follow(false)
    //             .urlEncodingEnabled(true)
    //             .param("uid", "111111").and()
    //             .param("amount", 10.01)
    //             .when().post("/pay/checkout")
    //             .then()
    //             .statusCode(303)
    //             .header("Location", containsString("sberbank.ru"));
    // }
}
