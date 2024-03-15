package ru.openfs.lbpay.resource.sberonline.exception;

import ru.openfs.lbpay.resource.sberonline.model.SberOnlineResponseType;

public class SberOnlineException extends RuntimeException {
    private final SberOnlineResponseType response;

    public SberOnlineException(SberOnlineResponseType response, String message) {
        super(message);
        this.response = response;
    }

    public SberOnlineResponseType getResponse() {
        return this.response;
    }
}
