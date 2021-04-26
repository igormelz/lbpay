package ru.openfs;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api3.Login;
import api3.Logout;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.ext.web.client.predicate.ErrorConverter;
import io.vertx.mutiny.ext.web.client.predicate.ResponsePredicate;

@Singleton
public class LbSoapService {
    private static final Logger LOG = LoggerFactory.getLogger(LbSoapService.class);
    private WebClient client;

    @ConfigProperty(name = "lbcore.host", defaultValue = "127.0.0.1")
    String host;

    @ConfigProperty(name = "lbcore.login")
    String login;

    @ConfigProperty(name = "lbcore.pass")
    String pass;

    @Inject
    ProducerTemplate producer;

    @Inject
    Vertx vertx;

    @PostConstruct
    void initialize() {
        this.client = WebClient.create(vertx, new WebClientOptions().setDefaultHost(host).setDefaultPort(34012));
    }

    public String login() {
        Login soapRequest = new Login();
        soapRequest.setLogin(login);
        soapRequest.setPass(pass);
        try {
            return callService(soapRequest, null).getString("sessionId");
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            return null;
        }
    }

    public void logout(String sessionId) {
        callService(new Logout(), sessionId);
    }

    public JsonObject callService(Object request, String sessionId) throws RuntimeException {
        Set<String> cookie = sessionId != null ? Collections.singleton(sessionId) : Collections.emptySet();
        return client.post("/").expect(predicate).putHeader("Cookie", cookie)
                .putHeader("Content-type", "application/xml")
                .sendBuffer(Buffer.buffer(producer.requestBody("direct:marshalSoap", request, byte[].class))).onItem()
                .transform(response -> {
                    JsonObject json = response.headers().contains("Set-Cookie")
                            ? new JsonObject().put("sessionId",
                                    response.getHeader("Set-Cookie").replaceFirst("(sessnum=\\w+);.*", "$1"))
                            : new JsonObject();
                    json.put("data", JsonObject
                            .mapFrom(producer.requestBody("direct:unmarshalSoap", response.bodyAsBuffer().getBytes())));
                    return json;
                }).await().atMost(Duration.ofSeconds(3));
    }

    ErrorConverter converter = ErrorConverter.createFullBody(result -> {
        HttpResponse<Buffer> response = result.response();
        String err = producer.requestBody("direct:getFaultMessage", response.bodyAsString(), String.class);
        if (err != null && !err.isBlank())
            return new RuntimeException(err);
        return new RuntimeException(result.message());
    });

    ResponsePredicate predicate = ResponsePredicate.create(ResponsePredicate.SC_SUCCESS, converter);

}