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

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;
import ru.openfs.lbpay.resource.checkout.exception.CheckoutException;
import ru.openfs.lbpay.service.CheckoutService;

public abstract class AbstractCheckoutService implements CheckoutService {

    @Inject
    LbCoreSoapClient lbCoreSoapClient;

    /**
     * validate if account is active
     * 
     * @param  account the account number to test
     * @return         TRUE if active, FALSE otherwise
     */
    public boolean isActiveAccount(String account) {
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {
            return adapter.isActiveAccount(account);
        } catch (Exception e) {
            throw new CheckoutException(e.getMessage());
        }
    }

    /**
     * process checkout as creating prepayment on billing and create payment on bank
     * 
     * @param  account the account to checkout
     * @param  amount  tha amount
     * @return         url string to payment page
     */
    public String processCheckout(String account, Double amount) {
        Log.infof("checkout account: %s, amount: %.2f", account, amount);
        try (var adapter = lbCoreSoapClient.getSessionAdapter()) {

            // get active agreement id
            var agrmId = adapter.getAgreementIdByAccount(account)
                    .orElseThrow(() -> new CheckoutException("account inactive"));

            // create prepayment ordernumber
            var orderNumber = adapter.createOrderNumber(agrmId, amount);

            // create payement
            return createPayment(orderNumber, account, amount);

        } catch (Exception e) {
            throw new CheckoutException(e.getMessage());
        }
    }

    abstract String createPayment(Long orderNumber, String account, Double amount);

}
