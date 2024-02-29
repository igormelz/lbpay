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
package ru.openfs.lbpay.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.annotation.PostConstruct;
import ru.openfs.lbpay.dto.dreamkas.Operation;
import ru.openfs.lbpay.model.AuditOrder;
import ru.openfs.lbpay.model.AuditRecord;
import ru.openfs.lbpay.model.ReceiptOrder;

@ApplicationScoped
public class AuditDAO {

    @ConfigProperty(name = "audit.schema.create", defaultValue = "false")
    boolean schemaCreate;

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

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
                                           "PRIMARY KEY (mdOrder)," +
                                           "INDEX idx_order (orderNumber))")
                        .execute())
                .await().indefinitely();
    }

    // CREATE
    public void createOperation(ReceiptOrder order) {
        client.preparedQuery(
                "INSERT ReceiptOperation SET mdOrder = ?, orderNumber = ?, account = ?, amount = ?, email = ?, phone = ?")
                .execute(Tuple.of(order.mdOrder(), order.orderNumber(), order.account(), order.amount(), order.info().email(),
                        order.info().phone()))
                .await().indefinitely();
    }

    // UPDATE 
    public void updateOperation(Operation operation) {
        client.preparedQuery("UPDATE ReceiptOperation SET operId = ?, status = ? WHERE mdOrder = ?")
                .execute(Tuple.of(operation.id(), operation.status().name(), operation.externalId()))
                .subscribe().with(i -> {
                });
    }

    // DELETE
    public void deleteOperation(Operation operation) {
        client.preparedQuery("DELETE FROM ReceiptOperation WHERE mdOrder = ?")
                .execute(Tuple.of(operation.externalId())).await().indefinitely();
    }

    public Uni<Boolean> deleteOperationByOrderNumber(String orderNumber) {
        return client.preparedQuery("DELETE FROM ReceiptOperation WHERE orderNumber = ?").execute(Tuple.of(orderNumber))
                .onItem().transform(rowSet -> rowSet.rowCount() == 1);
    }

    // GET 
    public Uni<AuditRecord> findById(String mdOrder) {
        return client
                .preparedQuery("SELECT * FROM ReceiptOperation WHERE mdOrder = ?")
                .execute(Tuple.of(mdOrder))
                .onItem().transform(Iterable::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public Uni<AuditRecord> findByOrderNumber(String orderNumber) {
        return client
                .preparedQuery("SELECT * FROM ReceiptOperation WHERE orderNumber = ?")
                .execute(Tuple.of(orderNumber))
                .onItem().transform(set -> set.iterator())
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public Multi<AuditRecord> findAll() {
        return client.preparedQuery("SELECT * FROM ReceiptOperation").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AuditDAO::from);
    }

    private static AuditRecord from(Row row) {
        return new AuditRecord(
                row.getString("mdOrder"),
                row.getString("orderNumber"),
                row.getString("account"),
                row.getString("phone"),
                row.getString("email"),
                row.getDouble("amount"),
                row.getString("operId"),
                row.getString("status"),
                row.getLocalDateTime("createAt"));
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
                .onItem().transform(AuditDAO::orderFrom);
    }

    public void setWaitOrder(long orderNumber) {
        client.preparedQuery("UPDATE billing.pre_payments SET comment = 'wait deposited' WHERE record_id = ?")
                .execute(Tuple.of(orderNumber)).subscribe().with(
                        result -> Log.infof("mark orderNumber: %d as waiting deposited", orderNumber),
                        failure -> Log.error(failure));
    }

    public Uni<Boolean> clearOrder(String orderNumber) {
        return client.preparedQuery("UPDATE billing.pre_payments " +
                                    "SET cancel_date = CURRENT_TIMESTAMP, comment = CONCAT(comment,':CLEAR'), status = 2 " +
                                    "WHERE record_id = ? AND status = 0")
                .execute(Tuple.of(orderNumber))
                .onItem().transform(rowSet -> rowSet.rowCount() == 1);
    }

}
