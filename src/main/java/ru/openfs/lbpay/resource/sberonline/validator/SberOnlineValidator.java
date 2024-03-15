package ru.openfs.lbpay.resource.sberonline.validator;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import ru.openfs.lbpay.resource.sberonline.exception.SberOnlineException;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineRequest;
import ru.openfs.lbpay.resource.sberonline.model.SberOnlineResponseType;

public class SberOnlineValidator {
    public static final DateTimeFormatter PAY_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH:mm:ss");
    public static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    enum SberOnlineOperation {
        CHECK,
        PAYMENT
    }

    SberOnlineValidator() {}

    private static String getPayDate(String payDate) {
        try {
            return LocalDateTime.parse(payDate, PAY_DATE_FMT).format(BILL_DATE_FMT);
        } catch (DateTimeException ex) {
            throw new SberOnlineException(SberOnlineResponseType.WRONG_FORMAT_DATE, "bad format pay_date" + ex.getMessage());
        }
    }

    public static SberOnlineRequest validateRequest(String action, String account, Double amount, String payId, String payDate)
            throws SberOnlineException {
        if (!account.matches("\\d+$"))
            throw new SberOnlineException(SberOnlineResponseType.ACCOUNT_WRONG_FORMAT, "wrong format account: " + account);

        SberOnlineOperation operation = null;
        try {
            operation = SberOnlineOperation.valueOf(action.toUpperCase());
        } catch (Exception e) {
            throw new SberOnlineException(SberOnlineResponseType.WRONG_ACTION, "wrong action: " + action);
        }

        if (operation == SberOnlineOperation.PAYMENT) {
            if (amount == null || amount <= 0)
                throw new SberOnlineException(SberOnlineResponseType.PAY_AMOUNT_TOO_SMALL, "too small amount:" + amount);
            if (payDate == null)
                throw new SberOnlineException(SberOnlineResponseType.WRONG_FORMAT_DATE, "pay_date is required");

            return new SberOnlineRequest(false, account, amount, payId, getPayDate(payDate));
        }

        return new SberOnlineRequest(true, account, null, null, null);
    }

}
