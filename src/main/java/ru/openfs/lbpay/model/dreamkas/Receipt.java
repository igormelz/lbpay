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

import java.util.List;

import ru.openfs.lbpay.model.dreamkas.type.OperationType;
import ru.openfs.lbpay.model.dreamkas.type.PaymentType;
import ru.openfs.lbpay.model.dreamkas.type.PositionType;
import ru.openfs.lbpay.model.dreamkas.type.TaxMode;
import ru.openfs.lbpay.model.dreamkas.type.VatType;

public record Receipt(String externalId, Integer deviceId, OperationType type, Integer timeout, TaxMode taxMode,
                      List<Position> positions, List<Payment> payments, Attributes attributes, Total total) {
    // default 
    public Receipt(String externalId, Integer deviceId, Integer price, String email, String phone) {
        this(
             externalId,
             deviceId,
             OperationType.SALE,
             15,
             TaxMode.SIMPLE_WO,
             List.of(new Position("Оплата услуг", PositionType.SERVICE, 1, price, price, VatType.NDS_NO_TAX, 0)),
             List.of(new Payment(price, PaymentType.CASHLESS)),
             new Attributes(email, phone),
             new Total(price));
    }
}
