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
public class AuditOrder {
    public int orderNumber;
    public LocalDateTime payDate;
    public String comments;
    public int diff;
    
    public AuditOrder() {
        // default constructor
    }

    public AuditOrder(int orderNumber, LocalDateTime payDate, String comments, int diff) {
        this.orderNumber = orderNumber;
        this.payDate = payDate;
        this.comments = comments;
        this.diff = diff;
    }
}
