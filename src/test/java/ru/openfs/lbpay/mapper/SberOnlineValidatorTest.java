package ru.openfs.lbpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ru.openfs.lbpay.resource.sberonline.exception.SberOnlineException;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineResponseType;
import ru.openfs.lbpay.resource.sberonline.validator.SberOnlineValidator;

class SberOnlineValidatorTest {

    @Test
    void testValidateRequestCheck() {
        var req = SberOnlineValidator.validateRequest("check", "12345", null, null, null);
        assertTrue(req.isCheckOperation());
        assertEquals("12345", req.account());

        SberOnlineException ex = assertThrows(SberOnlineException.class,
                () -> SberOnlineValidator.validateRequest("check", "S12345", null, null, null));
        assertEquals(SberOnlineResponseType.ACCOUNT_WRONG_FORMAT, ex.getResponse());
    }

    @Test
    void testValidateRequestPayment() {
        var req = SberOnlineValidator.validateRequest("payment", "12345", 111.00, "1", "13.02.2024_12:00:01");
        assertFalse(req.isCheckOperation());
        assertEquals("12345", req.account());

        SberOnlineException ex = assertThrows(SberOnlineException.class,
                () -> SberOnlineValidator.validateRequest("payment", "12345", 0.0, "1", "13.02.2024_12:00:01"));
        assertEquals(SberOnlineResponseType.PAY_AMOUNT_TOO_SMALL, ex.getResponse());
    }

    // @Test
    // void testFromPaymentResponse() {
    //     var response
    //             = new SberOnlinePaymentResponse(101010L, 1.0, LocalDateTime.now().format(SberOnlineMapper.BILL_DATE_FMT), null);
    //     var message = SberOnlineMapper.fromPaymentResponse(response);
    //     assertEquals(SberOnlineResponseCode.OK.getMsg(), message.getMessage());

    //     var dup = new SberOnlinePaymentResponse(101010L, null, LocalDateTime.now().format(SberOnlineMapper.BILL_DATE_FMT), 1.0);
    //     var dupMsg = SberOnlineMapper.fromPaymentResponse(dup);
    //     assertEquals(SberOnlineResponseCode.PAY_TRX_DUPLICATE.getMsg(), dupMsg.getMessage());
    // }

    // @Test
    // void testMarshalXML() throws Exception {
    //         var so = new Message(SberOnlineResponseCode.BACKEND_ERR);
    //         JAXBContext context = JAXBContext.newInstance(Message.class);
    //         Marshaller mar = context.createMarshaller();
    //         mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    //         StringWriter sw = new StringWriter();
    //         mar.marshal(so, sw);
    //         assertEquals(true, sw.toString().contains("<CODE>300</CODE>"));
    // }
}
