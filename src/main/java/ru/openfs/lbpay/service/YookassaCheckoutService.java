package ru.openfs.lbpay.service;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.YookassaClient;
import ru.openfs.lbpay.exception.CheckoutException;
import ru.openfs.lbpay.mapper.YookassaBuilder;

@ApplicationScoped
public class YookassaCheckoutService extends AbstractCheckoutService implements CheckoutService {

    @ConfigProperty(name = "yookassa.return.url", defaultValue = "http://localhost")
    String successUrl;

    @Inject
    @RestClient
    YookassaClient yookassaClient;

    @Override
    public String createPayment(Long orderNumber, String account, Double amount) {
        Log.debugf("try yookassa checkout orderNumber:%d, account: %s, amount: %.2f",
                orderNumber, account, amount);
        try {
            var response = yookassaClient.payments(YookassaBuilder.createRequest(orderNumber, account, amount, successUrl));

            return Optional.ofNullable(response.confirmation())
                    .map(c -> {
                        Log.infof("yookassa checkout for %d: account: %s, amount: %.2f, mdOrder: %s",
                                orderNumber, account, amount, response.id());
                        return c.confirmationUrl();
                    }).orElseThrow(() -> new CheckoutException("no confiramtion url"));

        } catch (RuntimeException e) {
            throw new CheckoutException(e.getMessage());
        }
    }

}
