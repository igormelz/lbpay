package ru.openfs.lbpay.audit;

import javax.inject.Singleton;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class SendMessage extends RouteBuilder {

    @ConfigProperty(name = "telegram.token")
    String token;

    @ConfigProperty(name = "telegram.chatid")
    String chatId;

    @Override
    public void configure() throws Exception {
        from("direct:sendMessage").routeId("SendBotMessage")
        .toF("telegram:bots?authorizationToken=%s&chatId=%s",token,chatId);
    }
    
}
