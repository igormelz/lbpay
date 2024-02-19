package ru.openfs.lbpay.resource;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.model.SberOnlineCheckResponse;
import ru.openfs.lbpay.model.SberOnlinePaymentResponse;
import ru.openfs.lbpay.model.SberOnlineRequest;
import ru.openfs.lbpay.service.SberOnlineService;

import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class SberOnlineResourceTest {

    @Inject
    MockSberOnlineService service;

    @Test
    void testProcessCheck() throws Exception {
        given()
            .param("ACTION", "check")
            .param("ACCOUNT", "123456")
        .when()
            .get("/pay/sber/online")
        .then()
            .statusCode(200)
            .body(containsString("<BALANCE>1.0</BALANCE>"))
            .and().body(containsString("<REC_SUM>10.0</REC_SUM>"))
            .and().body(containsString("<ADDRESS>SPB</ADDRESS>"));
    }

    @Test
    void testProcessPayment() throws Exception {
        given()
            .param("ACTION", "payment")
            .param("ACCOUNT", "123456")
            .param("AMOUNT", "1.1")
            .param("PAY_ID", "654321")
            .param("PAY_DATE", "01.01.2024_12:31:00")
        .when()
            .get("/pay/sber/online")
        .then()
            .statusCode(200)
            .body(containsString("<EXT_ID>101010</EXT_ID>"))
            .and().body(containsString("<SUM>1.1</SUM>"))
            .and().body(containsString("<REG_DATE>01.01.2024_12:32:00</REG_DATE>"));
    }

    @Mock
    @ApplicationScoped
    public static class MockSberOnlineService extends SberOnlineService {
        @Override
        public SberOnlineCheckResponse processCheckAccount(String account) {
            return new SberOnlineCheckResponse(1.0, 10.0, "SPB");
        }

        @Override
        public SberOnlinePaymentResponse processPayment(SberOnlineRequest request) {
            return new SberOnlinePaymentResponse(101010L, request.amount(), "2024-01-01 12:32:00", null);
        }
    }
}
