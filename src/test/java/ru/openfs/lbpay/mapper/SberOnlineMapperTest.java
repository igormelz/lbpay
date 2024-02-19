package ru.openfs.lbpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringWriter;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import ru.openfs.lbpay.dto.sberonline.SberOnlineMessage;
import ru.openfs.lbpay.exception.SberOnlineException;
import ru.openfs.lbpay.model.SberOnlineCheckResponse;
import ru.openfs.lbpay.model.SberOnlinePaymentResponse;
import ru.openfs.lbpay.model.type.SberOnlineOperation;
import ru.openfs.lbpay.model.type.SberOnlineResponseCode;

class SberOnlineMapperTest {

    @Test
    void testValidateRequestCheck() {
        var req = SberOnlineMapper.validateRequest("check", "12345", null, null, null);
        assertEquals(SberOnlineOperation.CHECK, req.operation());
        assertEquals("12345", req.account());

        SberOnlineException ex = assertThrows(SberOnlineException.class,
                () -> SberOnlineMapper.validateRequest("check", "S12345", null, null, null));
        assertEquals(SberOnlineResponseCode.ACCOUNT_WRONG_FORMAT, ex.getResponse());
    }

    @Test
    void testValidateRequestPayment() {
        var req = SberOnlineMapper.validateRequest("payment", "12345", 111.00, "1", "13.02.2024_12:00:01");
        assertEquals(SberOnlineOperation.PAYMENT, req.operation());
        assertEquals("12345", req.account());

        SberOnlineException ex = assertThrows(SberOnlineException.class,
                () -> SberOnlineMapper.validateRequest("payment", "12345", null, null, null));
        assertEquals(SberOnlineResponseCode.PAY_AMOUNT_TOO_SMALL, ex.getResponse());
    }

    @Test
    void testFromCheckResponse() {
        var response = new SberOnlineCheckResponse(0.1, 1.0, "address");
        var message = SberOnlineMapper.fromCheckResponse(response);
        assertEquals(SberOnlineResponseCode.OK.getMsg(), message.getMessage());
    }

    @Test
    void testFromPaymentResponse() {
        var response
                = new SberOnlinePaymentResponse(101010L, 1.0, LocalDateTime.now().format(SberOnlineMapper.BILL_DATE_FMT), null);
        var message = SberOnlineMapper.fromPaymentResponse(response);
        assertEquals(SberOnlineResponseCode.OK.getMsg(), message.getMessage());

        var dup = new SberOnlinePaymentResponse(101010L, null, LocalDateTime.now().format(SberOnlineMapper.BILL_DATE_FMT), 1.0);
        var dupMsg = SberOnlineMapper.fromPaymentResponse(dup);
        assertEquals(SberOnlineResponseCode.PAY_TRX_DUPLICATE.getMsg(), dupMsg.getMessage());
    }

    @Test
    void testMarshalXML() throws Exception {
            var so = new SberOnlineMessage(SberOnlineResponseCode.BACKEND_ERR);
            JAXBContext context = JAXBContext.newInstance(SberOnlineMessage.class);
            Marshaller mar = context.createMarshaller();
            mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            StringWriter sw = new StringWriter();
            mar.marshal(so, sw);
            assertEquals(true, sw.toString().contains("<CODE>300</CODE>"));
    }
}
