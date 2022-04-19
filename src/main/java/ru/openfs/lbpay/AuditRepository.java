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

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import ru.openfs.lbpay.model.AuditOrder;
import ru.openfs.lbpay.model.AuditRecord;

@ApplicationScoped
public class AuditRepository {

    @ConfigProperty(name = "audit.schema.create", defaultValue = "false")
    boolean schemaCreate;

    @Inject
    EventBus bus;

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
                .execute(Tuple.of(order.getString("mdOrder"),
                        order.getString("orderNumber"),
                        order.getString("account"),
                        order.getDouble("amount"),
                        order.getString("email"),
                        order.getString("phone")))
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
                .execute(Tuple.of(operation.getString("id"), operation.getString("status"),
                        operation.getString("externalId")))
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
                row.getString("mdOrder"),
                row.getString("orderNumber"),
                row.getString("account"),
                row.getDouble("amount"),
                row.getString("phone"),
                row.getString("email"),
                row.getLocalDateTime("createAt"),
                row.getString("operId"),
                row.getString("status"));
    }

    private static AuditOrder orderFrom(Row row) {
        return new AuditOrder(
                row.getInteger("record_id"),
                row.getLocalDateTime("pay_date"),
                row.getString("comment"),
                row.getInteger("diff"));
    }

    public Multi<AuditOrder> getPendingOrders() {
        return client.query("SELECT record_id, pay_date, comment, " +
                "unix_timestamp(current_timestamp) - unix_timestamp(pay_date) as diff " +
                "FROM billing.pre_payments WHERE status = 0")
                .execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AuditRepository::orderFrom);
    }

    public void setWaitOrder(long orderNumber) {
        client.preparedQuery("UPDATE billing.pre_payments SET comment = 'wait deposited' WHERE record_id = ?")
                .execute(Tuple.of(orderNumber)).subscribe().with(
                        result -> Log.info(String.format(
                                "mark orderNumber: %d as waiting deposited",
                                orderNumber)),
                        failure -> Log.error(failure));
    }

    public Uni<Boolean> clearOrder(long orderNumber) {
        return client.preparedQuery("UPDATE billing.pre_payments " +
                "SET cancel_date = CURRENT_TIMESTAMP, comment = CONCAT(comment,':CLEAR'), status = 2 " +
                "WHERE record_id = ?")
                .execute(Tuple.of(orderNumber))
                .onItem().transform(row -> row.rowCount() == 1);
    }

}
