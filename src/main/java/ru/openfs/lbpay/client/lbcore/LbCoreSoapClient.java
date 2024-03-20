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
package ru.openfs.lbpay.client.lbcore;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.ext.web.client.predicate.ErrorConverter;
import io.vertx.mutiny.ext.web.client.predicate.ResponsePredicate;
import jakarta.annotation.PostConstruct;

@Singleton
public class LbCoreSoapClient {
    
    private WebClient client;

    @ConfigProperty(name = "lbcore.host", defaultValue = "127.0.0.1")
    String host;

    @ConfigProperty(name = "lbcore.login", defaultValue = "login")
    String login;

    @ConfigProperty(name = "lbcore.pass", defaultValue = "pass")
    String pass;

    @Inject
    ProducerTemplate producer;

    @Inject
    Vertx vertx;

    @PostConstruct
    void initialize() {
        this.client = WebClient.create(vertx, new WebClientOptions().setDefaultHost(host).setDefaultPort(34012));
    }

    public LbCoreSessionAdapter getSessionAdapter() {
        return new LbCoreSessionAdapter(this);
    }

    protected <T> T getMandatoryResponse(Object request, String sessionId, Class<T> clazz) {
        return callService(request, sessionId).getJsonObject("data").mapTo(clazz);
    }

    protected JsonObject callService(Object request, String sessionId) throws RuntimeException {
        Set<String> cookie = sessionId != null ? Collections.singleton(sessionId) : Collections.emptySet();
        return client.post("/").expect(predicate).putHeader("Cookie", cookie)
                .putHeader("Content-type", "application/xml")
                .sendBuffer(Buffer.buffer(producer.requestBody("direct:marshalSoap", request, byte[].class))).onItem()
                .transform(response -> {
                    JsonObject json = response.headers().contains("Set-Cookie")
                            ? new JsonObject().put("sessionId", parseSessionId(response.cookies()))
                            : new JsonObject();
                    json.put("data", JsonObject
                            .mapFrom(producer.requestBody("direct:unmarshalSoap", response.bodyAsBuffer().getBytes())));
                    return json;
                }).await().indefinitely(); 
    }

    ErrorConverter converter = ErrorConverter.createFullBody(result -> {
        HttpResponse<Buffer> response = result.response();
        String err = producer.requestBody("direct:getFaultMessage", response.bodyAsString(), String.class);
        if (err != null && !err.isBlank())
            return new RuntimeException(err);
        return new RuntimeException(result.message());
    });

    ResponsePredicate predicate = ResponsePredicate.create(ResponsePredicate.SC_SUCCESS, converter);

    private static String parseSessionId(List<String> cookie) {
        return cookie.stream().filter(c -> c.startsWith("sessnum") && c.contains("Max-Age")).findAny()
                .map(c -> c.substring(0, c.indexOf(";"))).orElse(null);
    }

}
