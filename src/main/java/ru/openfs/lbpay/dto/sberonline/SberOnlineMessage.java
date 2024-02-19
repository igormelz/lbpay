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
package ru.openfs.lbpay.dto.sberonline;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import ru.openfs.lbpay.model.type.SberOnlineResponseCode;

import java.io.Serializable;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@XmlRootElement(name = "response")
public class SberOnlineMessage implements Serializable {
    @XmlElement(name = "CODE")
    int code;
    @XmlElement(name = "MESSAGE")
    String message;
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

    public SberOnlineMessage(SberOnlineResponseCode response) {
        this.code = response.getCode();
        this.message = response.getMsg();
    }

    public void setResponse(SberOnlineResponseCode response) {
        this.code = response.getCode();
        this.message = response.getMsg();   
    }

    public String getMessage() {
        return message;
    }

    public void setFio(String fio) {
        this.fio = fio;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public void setRegDate(String regDate) {
        this.regDate = regDate;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setSum(double sum) {
        this.sum = sum;
    }

    public void setRecSum(double recSum) {
        this.recSum = recSum;
    }

    public void setExtId(Long extId) {
        this.extId = extId;
    }
}
