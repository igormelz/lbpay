/*
 * Copyright [2021] [OpenFS.RU]
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

import javax.xml.bind.annotation.XmlRootElement;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@XmlRootElement(name = "response")
public class SberOnlineMessage {
    public int CODE;
    public String MESSAGE;
    public String FIO;
    public String ADDRESS;
    public Double BALANCE;
    public String INFO;
    public String REG_DATE;
    public Double AMOUNT;
    public Double SUM;
    public Double REC_SUM;
    public Long EXT_ID;

    public SberOnlineMessage() {}

    public SberOnlineMessage(SberOnlineCode code) {
        this.CODE = code.getCode();
        this.MESSAGE = code.getMsg();
    }

    public void setResponse(SberOnlineCode code) {
        this.CODE = code.getCode();
        this.MESSAGE = code.getMsg();
    }
}
