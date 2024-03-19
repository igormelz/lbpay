package ru.openfs.lbpay.model;

import java.util.List;

import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PrePaymentsDao {
    @Inject
    MySQLPool billing;

    public List<PrePayment> getPendingOrders() {
        var sql = """
                    SELECT 
                        record_id, 
                        pay_date, 
                        comment,
                        unix_timestamp(current_timestamp) - unix_timestamp(pay_date) as diff
                    FROM
                        billing.pre_payments
                    WHERE
                        status = 0
                """;
        return billing.query(sql)
                .execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(this::orderFrom).collect().asList().await().indefinitely();
    }

    private PrePayment orderFrom(Row row) {
        return new PrePayment(
                row.getInteger("record_id"),
                row.getLocalDateTime("pay_date"),
                row.getString("comment"),
                row.getInteger("diff"));
    }
}
