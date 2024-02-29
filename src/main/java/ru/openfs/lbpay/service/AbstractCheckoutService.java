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
package ru.openfs.lbpay.service;

import java.util.Optional;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.LbCoreSoapClient;
import ru.openfs.lbpay.exception.CheckoutException;

public abstract class AbstractCheckoutService {

    @Inject
    LbCoreSoapClient lbSoapService;

    // connect to billing
    protected String getSession() {
        return Optional.ofNullable(lbSoapService.login())
                .orElseThrow(() -> new CheckoutException("no lb session"));
    }

    /**
     * validate if account is active 
     * 
     * @param  account the account number to test
     * @return TRUE if active, FALSE otherwise
     */
    public boolean isActiveAccount(String account) {
        final String sessionId = getSession();
        try {
            return lbSoapService.isActiveAgreement(sessionId, account);
        } finally {
            lbSoapService.logout(sessionId);
        }
    }

    /**
     * process checkout as creating prepayment on billing and create payment on bank
     * 
     * @param  account the account to checkout
     * @param  amount  tha amount
     * @return url string to payment page
     */
    public String processCheckout(String account, Double amount) {
        Log.infof("checkout account: %s, amount: %.2f", account, amount);
        final String sessionId = getSession();
        try {

            // get active agreement id
            var agrmId = lbSoapService.getAgreementId(sessionId, account);
            if (agrmId == 0)
                throw new CheckoutException("account inactive");

            // create prepayment ordernumber
            var orderNumber = lbSoapService.createOrderNumber(sessionId, agrmId, amount);

            // create payement
            return createPayment(orderNumber, account, amount);

        } catch (RuntimeException e) {
            throw new CheckoutException(e.getMessage());
        } finally {
            lbSoapService.logout(sessionId);
        }
    }

    abstract String createPayment(Long orderNumber, String account, Double amount);

}
