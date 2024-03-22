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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import ru.openfs.lbpay.client.sberbank.SberClient;
import ru.openfs.lbpay.exception.CheckoutException;
import ru.openfs.lbpay.model.sberbank.RegisterResponse;

@ApplicationScoped
public class SberbankCheckoutService extends AbstractCheckoutService {

    @ConfigProperty(name = "sber.user", defaultValue = "user-api")
    String userName;

    @ConfigProperty(name = "sber.pass", defaultValue = "user-pass")
    String userPass;

    @ConfigProperty(name = "sber.success.url", defaultValue = "ok")
    String successUrl;

    @ConfigProperty(name = "sber.fail.url", defaultValue = "err")
    String failUrl;

    @Inject
    @RestClient
    SberClient sberClient;

    @Inject
    ObjectMapper mapper;

    @Override
    public String createPayment(Long orderNumber, String account, Double amount) {
        Log.debugf("try sber checkout orderNumber:%d, account: %s, amount: %.2f",
                orderNumber, account, amount);
        try {
            var request = createRequest(orderNumber, account, amount);
            var sber = mapper.readValue(sberClient.register(request), RegisterResponse.class);
            // test error
            if (sber.errorCode() != null && sber.errorCode() != 0)
                throw new CheckoutException(sber.errorMessage());

            Log.infof("sber checkout orderNumber: %d, account: %s, amount: %.2f, mdOrder: %s",
                    orderNumber, account, amount, sber.orderId());
            return sber.formUrl();

        } catch (RuntimeException | JsonProcessingException e) {
            throw new CheckoutException(e.getMessage());
        }
    }

    // map sber register request 
    private MultivaluedMap<String, String> createRequest(Long orderNumber, String account, double amount) {
        MultivaluedMap<String, String> answer = new MultivaluedHashMap<>();
        answer.putSingle("userName", userName);
        answer.putSingle("password", userPass);
        answer.putSingle("orderNumber", Long.toString(orderNumber));
        answer.putSingle("amount", Long.toString((long) (amount * 100)));
        answer.putSingle("returnUrl", successUrl);
        answer.putSingle("failUrl", failUrl);
        answer.putSingle("description", account);
        answer.putSingle("currency", "643");
        answer.putSingle("language", "ru");
        answer.putSingle("pageView", "DESKTOP");
        answer.putSingle("sessionTimeoutSecs", "300");
        return answer;
    }
}
