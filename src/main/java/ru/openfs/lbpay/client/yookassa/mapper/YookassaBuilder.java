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
package ru.openfs.lbpay.client.yookassa.mapper;

import ru.openfs.lbpay.client.yookassa.model.Amount;
import ru.openfs.lbpay.client.yookassa.model.Confirmation;
import ru.openfs.lbpay.client.yookassa.model.MetaData;
import ru.openfs.lbpay.client.yookassa.model.PaymentMethod;
import ru.openfs.lbpay.client.yookassa.model.PaymentRequest;

public class YookassaBuilder {
    YookassaBuilder() {}

    public static PaymentRequest createRequest(Long orderNumber, String account, Double amount, String url) {
        return new PaymentRequest(
                new Amount(Double.toString(amount), "RUB"),
                true,
                new PaymentMethod("bank_card", null, null),
                new Confirmation("redirect", url, null),
                String.format("Оплата заказа №%d по договору %s", orderNumber, account),
                new MetaData(Long.toString(orderNumber))
        );
    }
}
