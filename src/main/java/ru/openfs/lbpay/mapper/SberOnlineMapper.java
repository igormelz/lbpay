package ru.openfs.lbpay.mapper;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import ru.openfs.lbpay.dto.sberonline.SberOnlineMessage;
import ru.openfs.lbpay.exception.SberOnlineException;
import ru.openfs.lbpay.model.SberOnlineCheckResponse;
import ru.openfs.lbpay.model.SberOnlinePaymentResponse;
import ru.openfs.lbpay.model.SberOnlineRequest;
import ru.openfs.lbpay.model.type.SberOnlineOperation;
import ru.openfs.lbpay.model.type.SberOnlineResponseCode;

public class SberOnlineMapper {
    public static final DateTimeFormatter PAY_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH:mm:ss");
    public static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    SberOnlineMapper() {}

    private static String getPayDate(String payDate) {
        try {
            return LocalDateTime.parse(payDate, PAY_DATE_FMT).format(BILL_DATE_FMT);
        } catch (DateTimeException ex) {
            throw new SberOnlineException(SberOnlineResponseCode.WRONG_FORMAT_DATE, "bad format pay_date" + ex.getMessage());
        }
    }

    public static String toRegDate(String dateTime) {
        return LocalDateTime.parse(dateTime, BILL_DATE_FMT).format(PAY_DATE_FMT);
    }

    public static SberOnlineRequest validateRequest(String action, String account, Double amount, String payId, String payDate)
            throws SberOnlineException {
        if (!account.matches("\\d+$"))
            throw new SberOnlineException(SberOnlineResponseCode.ACCOUNT_WRONG_FORMAT, "wrong format account: " + account);

        SberOnlineOperation operation = null;
        try {
            operation = SberOnlineOperation.valueOf(action.toUpperCase());
        } catch (Exception e) {
            throw new SberOnlineException(SberOnlineResponseCode.WRONG_ACTION, "wrong action: " + action);
        }

        if (operation == SberOnlineOperation.PAYMENT) {
            if (amount == null || amount <= 0)
                throw new SberOnlineException(SberOnlineResponseCode.PAY_AMOUNT_TOO_SMALL, "too small amount:" + amount);
            if(payDate == null) 
                throw new SberOnlineException(SberOnlineResponseCode.WRONG_FORMAT_DATE, "pay_date is required");

            return new SberOnlineRequest(operation, account, amount, payId, getPayDate(payDate));
        }
        
        return new SberOnlineRequest(operation, account, null, null, null);
    }

    public static SberOnlineMessage fromCheckResponse(SberOnlineCheckResponse response) {
        var message = new SberOnlineMessage(SberOnlineResponseCode.OK);
        message.setBalance(response.balance());
        message.setRecSum(response.recommended());
        message.setAddress(response.address());
        return message;
    }

    public static SberOnlineMessage fromPaymentResponse(SberOnlinePaymentResponse response) {
        if(response.amount() == null) {
            var message = new SberOnlineMessage(SberOnlineResponseCode.OK);
            message.setExtId(response.paymentId());
            message.setSum(response.paymentSum());
            message.setRegDate(toRegDate(response.regDate()));
            return message;
        }
        var message = new SberOnlineMessage(SberOnlineResponseCode.PAY_TRX_DUPLICATE);
        message.setExtId(response.paymentId());
        message.setAmount(response.amount());
        message.setRegDate(toRegDate(response.regDate()));
        return message;

    }

}
