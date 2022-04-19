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

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

@XmlRegistry
public class ObjectFactory {
    
    private final QName responseQNAME = new QName("", "response");

    public ObjectFactory() {}

    public SberOnlineMessage createSberOnlineMessageType() {
        return new SberOnlineMessage();
    }

    @XmlElementDecl(namespace = "", name = "response")
    public JAXBElement<SberOnlineMessage> createResponse(SberOnlineMessage value) {
        return new JAXBElement<SberOnlineMessage>(responseQNAME, SberOnlineMessage.class, null, value);
    }

}
