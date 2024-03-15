package ru.openfs.lbpay.service;

import java.util.Optional;

import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;

public class LbCoreService {

    @Inject
    public LbCoreSoapClient lbCoreSoapClient;

    // connect to billing
    protected String getSession() {
        return Optional.ofNullable(lbCoreSoapClient.login())
                .orElseThrow(() -> new RuntimeException("no lb session"));
    }

    protected void closeSession(String sessionId) {
        if (sessionId != null)
            lbCoreSoapClient.logout(sessionId);
    }

}
