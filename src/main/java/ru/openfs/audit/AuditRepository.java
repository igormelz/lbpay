package ru.openfs.audit;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;

@ApplicationScoped
public class AuditRepository {

    @ConfigProperty(name = "audit.schema.create", defaultValue = "false")
    boolean schemaCreate;

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    ProducerTemplate producer;

    @PostConstruct
    void config() {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS ReceiptOperation").execute()
                .flatMap(r -> client.query("CREATE TABLE ReceiptOperation (" +
                                           "mdOrder CHAR(64) NOT NULL," +
                                           "account VARCHAR(255)," +
                                           "amount DOUBLE," +
                                           "createAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                                           "email VARCHAR(255)," +
                                           "operId VARCHAR(64)," +
                                           "orderNumber VARCHAR(64)," +
                                           "phone VARCHAR(255)," +
                                           "status VARCHAR(64)," +
                                           "PRIMARY KEY (mdOrder))")
                        .execute())
                .await().indefinitely();
    }

    @ConsumeEvent(value = "notify-bot", blocking = true)
    public void notifyBot(JsonObject msgObject) {
        producer.sendBody("direct:sendMessage", msgObject.encode());
    }

    public void setOrder(JsonObject order) {
        client.preparedQuery(
                "INSERT ReceiptOperation SET mdOrder = ?, orderNumber = ?, account = ?, amount = ?, email = ?, phone = ?")
                .execute(Tuple.of(order.getString("mdOrder"), order.getString("orderNumber"), order.getString("account"),
                        order.getDouble("amount"), order.getString("email"), order.getString("phone")))
                .await().indefinitely();
    }

    public Uni<AuditRecord> findById(String key) {
        return client
                .preparedQuery(
                        "SELECT * FROM ReceiptOperation WHERE mdOrder = ?")
                .execute(Tuple.of(key))
                .onItem().transform(set -> set.iterator())
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public void setOperation(JsonObject operation) {
        client.preparedQuery("UPDATE ReceiptOperation SET operId = ?, status = ? WHERE mdOrder = ?")
                .execute(Tuple.of(operation.getString("id"), operation.getString("status"), operation.getString("externalId")))
                .subscribe().with(i -> {
                });
    }

    public void processOperation(JsonObject operation) {
        if (operation.getString("status").equalsIgnoreCase("SUCCESS")) {
            client.preparedQuery("DELETE FROM ReceiptOperation WHERE mdOrder = ?")
                    .execute(Tuple.of(operation.getString("externalId"))).await().indefinitely();
        } else {
            client.preparedQuery("UPDATE ReceiptOperation SET operId = ?, status = ? WHERE mdOrder = ?")
                    .execute(Tuple.of(operation.getString("id"), operation.getString("status"),
                            operation.getString("externalId")))
                    .await().indefinitely();
        }
    }

    public Multi<AuditRecord> findAll() {
        return client.preparedQuery("SELECT * FROM ReceiptOperation").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AuditRepository::from);
    }

    private static AuditRecord from(Row row) {
        return new AuditRecord(
                row.getString("mdOrder"), row.getString("orderNumber"), row.getString("account"), row.getDouble("amount"),
                row.getString("phone"), row.getString("email"), row.getLocalDateTime("createAt"), row.getString("operId"),
                row.getString("status"));
    }
}
