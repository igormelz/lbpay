package ru.openfs.dreamkas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.redis.client.Response;

@Singleton
public class DreamkasService {

    @Inject
    ReactiveRedisClient redisClient;

    Function<Response, Void> empty = response -> null;

    Uni<Void> setOrder(JsonObject request) {
        return redisClient.hset(List.of(request.getString("mdOrder"), "order", request.encode())).map(empty)
                .call(action -> redisClient.sadd(List.of("orders", request.getString("mdOrder"))).map(empty));
    }

    Uni<Void> setOperation(JsonObject operation) {
        if (operation.getString("status").equalsIgnoreCase("SUCCESS")) {
            return redisClient.del(List.of(operation.getString("externalId"))).map(empty)
                    .call(action -> redisClient.srem(List.of("orders", operation.getString("externalId"))).map(empty));
        } else {
            return redisClient.hset(List.of(operation.getString("externalId"), "operation", operation.encode()))
                    .map(empty);
        }
    }

    Uni<JsonObject> getOperation(String key) {
        return redisClient.hget(key, "operation")
                .map(op -> op == null ? new JsonObject() : new JsonObject(op.toString()));
    }

    Uni<JsonObject> getOrder(String key) {
        return redisClient.hget(key, "order").map(op -> op == null ? new JsonObject() : new JsonObject(op.toString()));
    }

    Uni<List<String>> orders() {
        return redisClient.smembers("orders").map(response -> {
            List<String> result = new ArrayList<>();
            for (Response r : response) {
                result.add(r.toString());
            }
            return result;
        });
    }

}
