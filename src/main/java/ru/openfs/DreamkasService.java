package ru.openfs;

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
        String key = "op:" + request.getString("mdOrder");
        return redisClient.hset(List.of(key, "request", request.encode())).map(empty);
    }

    Uni<Void> setOperation(JsonObject operation) {
        String key = "op:" + operation.getString("externalId");
        if (operation.getString("status").equalsIgnoreCase("SUCCESS")) {
            // cleanup success 
            return redisClient.del(List.of(key)).map(empty);
        } else {
            return redisClient.hset(List.of(key, "operation", operation.encode())).map(empty);
        }
    }

    Uni<String> getOperation(String key) {
        return redisClient.hget(key, "operation").map(op -> op == null ? "{}" : op.toString());
    }

    Uni<String> getOrder(String key) {
        return redisClient.hget(key, "request").map(op -> op == null ? "{}" : op.toString());
    }

    Uni<List<String>> keys() {
        return redisClient.keys("op:*").map(response -> {
            List<String> result = new ArrayList<>();
            for (Response r : response) {
                result.add(r.toString());
            }
            return result;
        });
    }

}
