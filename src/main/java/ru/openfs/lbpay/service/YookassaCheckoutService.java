package ru.openfs.lbpay.service;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import ru.openfs.lbpay.client.YookassaClient;
import ru.openfs.lbpay.exception.CheckoutException;
import ru.openfs.lbpay.mapper.YookassaMapper;

@ApplicationScoped
public class YookassaCheckoutService extends CheckoutServiceImpl implements CheckoutService {

    @ConfigProperty(name = "yookassa.return.url", defaultValue = "http://localhost")
    String successUrl;

    @RestClient
    YookassaClient yookassaClient;

    @Override
    public String createPayment(Long orderNumber, String account, Double amount) {
        Log.debugf("try yookassa checkout orderNumber:%d, account: %s, amount: %.2f",
                orderNumber, account, amount);
        try {
            var request = YookassaMapper.createRequest(orderNumber, account, amount, successUrl);
            var response = yookassaClient.payments(request);

            return Optional.ofNullable(response.confirmation())
                    .map(c -> {
                        Log.infof("yookassa checkout orderNumber: %d, account: %s, amount: %.2f, mdOrder: %s",
                                orderNumber, account, amount, response.id());
                        return c.confirmationUrl();
                    }).orElseThrow(() -> new CheckoutException("no confiramtion url"));

        } catch (RuntimeException e) {
            throw new CheckoutException(e.getMessage());
        }
    }

}
