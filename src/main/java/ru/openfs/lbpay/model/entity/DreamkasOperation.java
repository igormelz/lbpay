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
package ru.openfs.lbpay.model.entity;

import java.time.LocalDateTime;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import ru.openfs.lbpay.model.dreamkas.type.OperationStatus;

@Entity
public class DreamkasOperation extends PanacheEntity {
    /** dk opertion id */
    public String operationId;
    /** dk operation status */
    @Enumerated(EnumType.STRING)
    public OperationStatus operationStatus;
    /** dk external id */
    public String externalId;
    /** billing order number */
    public String orderNumber;
    /** billing account */
    public String account;
    /** payment amount */
    public Double amount;
    /** customer info.email */
    public String email;
    /** customer info.phone */
    public String phone;
    /** timestamp created record */
    public LocalDateTime createAt = LocalDateTime.now();

    public static Optional<DreamkasOperation> findByExternalId(String externalId) {
        return find("externalId", externalId).firstResultOptional();
    }

    public static Optional<DreamkasOperation> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }
}
