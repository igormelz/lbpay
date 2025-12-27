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
package ru.openfs.lbpay.model.dreamkas;

import ru.openfs.lbpay.model.dreamkas.type.*;
import ru.openfs.lbpay.utils.NdsCalculator;

import java.util.List;

public record Receipt(
        String externalId,
        Integer deviceId,
        OperationType type,
        Integer timeout,
        TaxMode taxMode,
        List<Position> positions,
        List<Payment> payments,
        Attributes attributes,
        List<FiscalTag> tags,
        Total total
) {
    // default
    public Receipt(String externalId, Integer deviceId, Integer price, String email,
                   String phone, String productName) {
        this(externalId, deviceId, OperationType.SALE, 15, TaxMode.SIMPLE_WO,
                List.of(new Position(productName, PositionType.SERVICE, 1, price, price, VatType.NDS_NO_TAX, 0)),
                List.of(new Payment(price, PaymentType.CASHLESS)),
                new Attributes(email, phone),
                null,
                new Total(price));
    }

    // nds5 + tag 1125
    public static Receipt createNds5(String externalId, Integer deviceId, Integer price, String email,
                                     String phone, String productName) {
        return new Receipt(externalId, deviceId, OperationType.SALE, 15, TaxMode.SIMPLE_WO,
                List.of(new Position(productName, PositionType.SERVICE, 1, price, price,
                        VatType.NDS_5, NdsCalculator.extractNDS5(price))),
                List.of(new Payment(price, PaymentType.CASHLESS)),
                new Attributes(email, phone), null, new Total(price));
    }

    // nds5 + tag 1125
    public static Receipt createNds5WithTags(String externalId, Integer deviceId, Integer price, String email,
                                             String phone, String productName) {
        return new Receipt(externalId, deviceId, OperationType.SALE, 15, TaxMode.SIMPLE_WO,
                List.of(new Position(productName, PositionType.SERVICE, 1, price, price,
                        VatType.NDS_5, NdsCalculator.extractNDS5(price))),
                List.of(new Payment(price, PaymentType.CASHLESS)),
                new Attributes(email, phone),
                List.of(new FiscalTag(1125, 1)),
                new Total(price));
    }

}
