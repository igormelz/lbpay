package ru.openfs.audit;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.redis.client.Response;

@Singleton
public class AuditRepository {

    @Inject
    ReactiveRedisClient redisClient;

    // Function<Response, Void> empty = response -> null;

    public void setOrder(JsonObject request) {
        redisClient.hset(List.of(request.getString("mdOrder"), "order", request.encode())).subscribe().with(i -> {
        });
        redisClient.sadd(List.of("orders", request.getString("mdOrder"))).subscribe().with(i -> {
        });
    }

    public void setOperation(JsonObject operation) {
        if (operation.getString("status").equalsIgnoreCase("SUCCESS")) {
            redisClient.del(List.of(operation.getString("externalId"))).subscribe().with(i -> {
            });
            redisClient.srem(List.of("orders", operation.getString("externalId"))).subscribe().with(i -> {
            });
        } else {
            redisClient.hset(List.of(operation.getString("externalId"), "operation", operation.encode())).subscribe()
                    .with(i -> {
                    });
        }
    }

    public JsonObject getOperation(String key) {
        var op = redisClient.hget(key, "operation").await().indefinitely();
        return op == null ? new JsonObject() : new JsonObject(op.toString());
    }

    public JsonObject getOrder(String key) {
        var op = redisClient.hget(key, "order").await().indefinitely();
        return op == null ? new JsonObject() : new JsonObject(op.toString());
    }

    public List<String> orders() {
        return redisClient.smembers("orders").map(response -> {
            List<String> result = new ArrayList<>();
            for (Response order : response) {
                result.add(order.toString());
            }
            return result;
        }).await().indefinitely();
    }

}
