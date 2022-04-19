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
package ru.openfs.lbpay;

import javax.inject.Singleton;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class CamelSendMessage extends RouteBuilder {

    @ConfigProperty(name = "telegram.token", defaultValue = "TOKEN")
    String token;

    @ConfigProperty(name = "telegram.chatid", defaultValue = "123456789")
    String chatId;

    @Override
    public void configure() throws Exception {
        from("direct:sendMessage").routeId("SendBotMessage")
                .toF("telegram:bots?authorizationToken=%s&chatId=%s", token, chatId);
    }

}
