package ru.openfs.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class AuditRepository implements PanacheRepository<Operation> {

    @Inject
    ProducerTemplate producer;

    @ConsumeEvent("notify-bot")
    public void notifyBot(JsonObject msgObject) {
        producer.sendBody("direct:sendMessage", msgObject.encode());
    }

    public void setOrder(JsonObject order) {
        Operation entity = order.mapTo(Operation.class);
        entity.createAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        persist(entity);
    }

    public JsonObject getOrder(String key) {
        Operation op = find("mdOrder", key).firstResult();
        return op == null ? new JsonObject() : JsonObject.mapFrom(op);
    }

    public void setOperation(JsonObject operation) {
        if (operation.getString("status").equalsIgnoreCase("SUCCESS")) {
            delete("mdOrder", operation.getString("externalId"));
        } else {
            update("operId = ?1, status = ?2 where mdOrder = ?3", operation.getString("id"), operation.getString("status"),
                    operation.getString("externalId"));
        }
    }

    // public void setFd(JsonObject fd) {
    //     redisClient.hset(List.of("doc." + fd.getString("fiscalDocumentNumber"), "fd", fd.encode()));
    // }

    // public JsonObject getOperation(String key) {
    //     var op = redisClient.hget(ReceiptKey(key), "operation");
    //     return op == null ? new JsonObject() : new JsonObject(op.toString());
    // }

    public List<Operation> orders() {
        return listAll();
    }

}
