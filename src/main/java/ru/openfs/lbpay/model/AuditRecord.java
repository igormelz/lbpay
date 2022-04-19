/*
 * Copyright 2021,2022 OpenFS.RU
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
package ru.openfs.lbpay.model;

import java.time.LocalDateTime;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AuditRecord {
    public String mdOrder;
    public String orderNumber;
    public String account;
    public String phone;
    public String email;
    public Double amount;
    public String operId;
    public String status;
    public LocalDateTime createAt;

    public AuditRecord() {
        //default constructor
    }

    public AuditRecord(String mdOrder, String orderNumber, String account, Double amount, String phone, String email,
                       LocalDateTime createAt, String operId, String status) {
        this.mdOrder = mdOrder;
        this.account = account;
        this.orderNumber = orderNumber;
        this.email = email;
        this.phone = phone;
        this.amount = amount;
        this.createAt = createAt;
        this.operId = operId;
        this.status = status;
    }

}
