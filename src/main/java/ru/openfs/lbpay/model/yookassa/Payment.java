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
package ru.openfs.lbpay.model.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Payment(
    String id, 
    String status,
    Amount amount, 
    @JsonProperty("income_amount") Amount incomeAmount, 
    String description,
    Recepient recepient,
    @JsonProperty("payment_method") PaymentMethod paymentMethod,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("captured_at") String capturedAt,
    @JsonProperty("expires_at") String expiresAt, 
    Confirmation confirmation,
    MetaData metadata,
    Boolean test, Boolean paid, Boolean refundable) {}
