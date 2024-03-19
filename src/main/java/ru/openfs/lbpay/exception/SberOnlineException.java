package ru.openfs.lbpay.exception;

import ru.openfs.lbpay.model.type.SberOnlineResponseCode;

public class SberOnlineException extends RuntimeException {
    private final SberOnlineResponseCode response;

    public SberOnlineException(SberOnlineResponseCode response, String message) {
        super(message);
        this.response = response;
    }

    public SberOnlineResponseCode getResponse() {
        return this.response;
    }
}
