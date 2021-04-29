package ru.openfs.dreamkas;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.quarkus.redis.client.RedisClient;
import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.vertx.core.json.JsonObject;

@Singleton
public class DreamkasService {

    @Inject
    RedisClient redisClient;

    @Inject
    ReactiveRedisClient redisClientAsynch;

    // Function<Response, Void> empty = response -> null;

    void setOrder(JsonObject request) {
        redisClientAsynch.hset(List.of(request.getString("mdOrder"), "order", request.encode())).subscribe().with(i -> {
        });
        redisClientAsynch.sadd(List.of("orders", request.getString("mdOrder"))).subscribe().with(i -> {
        });
    }

    void setOperation(JsonObject operation) {
        if (operation.getString("status").equalsIgnoreCase("SUCCESS")) {
            redisClientAsynch.del(List.of(operation.getString("externalId"))).subscribe().with(i -> {
            });
            redisClientAsynch.srem(List.of("orders", operation.getString("externalId"))).subscribe().with(i -> {
            });
        } else {
            redisClientAsynch.hset(List.of(operation.getString("externalId"), "operation", operation.encode()))
                    .subscribe().with(i -> {
                    });
        }
    }

    JsonObject getOperation(String key) {
        var op = redisClient.hget(key, "operation");
        return op == null ? new JsonObject() : new JsonObject(op.toString());
    }

    JsonObject getOrder(String key) {
        var op = redisClient.hget(key, "order");
        return op == null ? new JsonObject() : new JsonObject(op.toString());
    }

    List<String> orders() {
        List<String> result = new ArrayList<>();
        redisClient.smembers("orders").forEach(response -> {
            result.add(response.toString());
        });
        return result;
    }

}
