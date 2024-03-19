/*
 * Copyright 2021-2024 OpenFS.RU
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.openfs.lbpay.service.impl;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.yookassa.YookassaClient;
import ru.openfs.lbpay.client.yookassa.mapper.YookassaBuilder;
import ru.openfs.lbpay.resource.checkout.exception.CheckoutException;

@ApplicationScoped
public class YookassaCheckoutService extends AbstractCheckoutService {

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
