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
package ru.openfs.lbpay.resource.sberonline.model;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import ru.openfs.lbpay.resource.sberonline.validator.SberOnlineValidator;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@XmlRootElement(name = "response")
public class SberOnlineMessage implements Serializable {
    @XmlElement(name = "CODE")
    int code;
    @XmlElement(name = "MESSAGE")
    String msg;
    @XmlElement(name = "FIO")
    String fio;
    @XmlElement(name = "ADDRESS")
    String address;
    @XmlElement(name = "BALANCE")
    Double balance;
    @XmlElement(name = "INFO")
    String info;
    @XmlElement(name = "REG_DATE")
    String regDate;
    @XmlElement(name = "AMOUNT")
    Double amount;
    @XmlElement(name = "SUM")
    Double sum;
    @XmlElement(name = "REC_SUM")
    Double recSum;
    @XmlElement(name = "EXT_ID")
    Long extId;

    public SberOnlineMessage() {}

    public SberOnlineMessage(Builder builder) {
        this.code = builder.code;
        this.msg = builder.msg;
        this.fio = builder.fio;
        this.address = builder.address;
        this.balance = builder.balance;
        this.info = builder.info;
        this.regDate = builder.regDate;
        this.amount = builder.amount;
        this.sum = builder.sum;
        this.recSum = builder.recSum;
        this.extId = builder.extId;
    }

    public static class Builder {   
        protected int code;
        protected String msg;
        protected String fio;
        protected String address;
        protected Double balance;
        protected String info;
        protected String regDate;
        protected Double amount;
        protected Double sum;
        protected Double recSum;
        protected Long extId;

        public Builder responseType(SberOnlineResponseType responseType) {
            this.code = responseType.getCode();
            this.msg = responseType.getMsg();
            return this;
        }

        public Builder setFio(String fio) {
            this.fio = fio;
            return this;
        }

        public Builder setAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder setBalance(double balance) {
            this.balance = balance;
            return this;
        }

        public Builder setInfo(String info) {
            this.info = info;
            return this;
        }

        public Builder setRegDate(String regDate) {
            this.regDate = LocalDateTime.parse(regDate, SberOnlineValidator.BILL_DATE_FMT).format(SberOnlineValidator.PAY_DATE_FMT);
            return this;
        }

        public Builder setAmount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder setSum(double sum) {
            this.sum = sum;
            return this;
        }

        public Builder setRecSum(double recSum) {
            this.recSum = recSum;
            return this;
        }

        public Builder setExtId(Long extId) {
            this.extId = extId;
            return this;
        }

        public SberOnlineMessage build() {
            return new SberOnlineMessage(this);
        }
    }
}
