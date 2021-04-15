package ru.openfs;

import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Tuple;

@ApplicationScoped
public class ReceiptService {
    private static final Logger log = Logger.getLogger(ReceiptService.class.getName());

    @Inject
    MySQLPool client;

    @ConsumeEvent("register")
    public void register(JsonObject rcpt) {
        // store to audit
        client.preparedQuery("INSERT INTO Rcpt SET amount=?, mdOrder=?, orderNumber=?, email=?, rcptType='SALE'")
                .execute(Tuple.of(rcpt.getDouble("amount"), rcpt.getString("mdOrder"), rcpt.getLong("orderNumber"),
                        rcpt.getString("email")))
                .subscribe().with(item -> {
                });
        // call to register
    }
}
