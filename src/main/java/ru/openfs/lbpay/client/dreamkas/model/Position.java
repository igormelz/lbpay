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
package ru.openfs.lbpay.client.dreamkas.model;

import ru.openfs.lbpay.client.dreamkas.model.type.PositionType;
import ru.openfs.lbpay.client.dreamkas.model.type.VatType;

public record Position(
    String name, 
    PositionType type, 
    Integer quantity, 
    /** цена в КОПЕЙКАХ за единицу расчета */
    Integer price, 
    /** цена позиции в КОПЕЙКАХ за единицу расчета */
    Integer priceSum,
    VatType tax,
    /** Сумма НДС (если не указан, то будет вычислен устройством) */
    Integer taxSum
){}