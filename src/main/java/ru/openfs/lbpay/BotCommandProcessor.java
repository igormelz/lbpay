package ru.openfs.lbpay;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;

@Singleton
public class BotCommandProcessor implements Processor {

    @Produce("direct:sendMessage")
    ProducerTemplate producer;

    @Inject
    AuditRepository audit;

    @Inject
    EventBus bus;

    @ConsumeEvent(value = "notify-error", blocking = true)
    public void notifyError(String message) {
        Log.error(message);
        producer.sendBody(Templates.notifyError(message));
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        //String chatId = exchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID, String.class);

        // parse command
        String command = body.replaceAll("^/([a-zA-Z_]+).*", "$1");
        String orderNumber = body.replaceAll("^/[a-zA-Z_]+(\\d*)", "$1");

        // process command
        switch (command) {
            case "pending":
                getPendingOrders();
                break;
            case "payment_status_":
                getOrderPaymentStatus(orderNumber);
                break;
            case "cancel_order_":
                doCancelOrder(orderNumber);
                break;
            case "wait":
                getWaitReceipts();
                break;
            default:
                Log.warnf("Unknown command: %s", command);
        }
    }

    private void getWaitReceipts() {
        Log.info("process command: wait");
        audit.findAll().collect().asList().subscribe().with(response -> {
            if (response.size() == 0) {
                producer.sendBody("🆗 no waiting receipts");
            } else {
                response.forEach(
                        receipt -> producer.sendBody(Templates.receipt(receipt).render()));
            }
        }, fail -> {
            producer.sendBody("Failed get wait receipts:" + fail.getMessage());
        });
    }

    private void doCancelOrder(String orderNumber) {
        Log.infof("process command: cancel orderNumber:%s", orderNumber);
        audit.clearOrder(orderNumber).subscribe().with(
                deleted -> {
                    if (deleted)
                        producer.sendBody("💳 Заказ #" + orderNumber + " 🚮 отменен");
                    else
                        producer.sendBody("💳 Заказ #" + orderNumber + " ❓не найден");
                },
                fail -> {
                    producer.sendBody("Failed cancel:" + fail.getMessage());
                });
    }

    private void getPendingOrders() {
        Log.info("process command: pending");
        audit.getPendingOrders().collect().asList().subscribe().with(
                orders -> {
                    if (orders.size() == 0) {
                        producer.sendBody("🆗 no pending orders");
                    } else {
                        orders.forEach(order -> producer.sendBody(Templates.order(order).render()));
                    }
                },
                fail -> {
                    producer.sendBody("Failed getPendingOrders:" + fail.getMessage());
                });
    }

    private void getOrderPaymentStatus(String orderNumber) {
        Log.infof("process command: payment status orderNumber: %s", orderNumber);
        bus.request("sber-payment-status", orderNumber).subscribe().with(
                status -> {
                    // keep answer
                    String text = status.body().toString();
                    // process answer as json
                    JsonObject json = JsonObject.mapFrom(status.body());
                    if (json.getString("errorCode").equalsIgnoreCase("0") && json.getInteger("actionCode") == 0) {
                        text = Templates.pay_status_ok(
                                json.getJsonObject("paymentAmountInfo").getString("paymentState"),
                                json.getString("orderNumber"),
                                json.getString("errorMessage")).render();
                    } else if (json.getString("errorCode").equalsIgnoreCase("6")) {
                        text = Templates.pay_status_notfound(orderNumber, json.getString("errorMessage")).render();
                    } else {
                        text = Templates.pay_status_fail(
                                json.getJsonObject("paymentAmountInfo").getString("paymentState"),
                                json.getString("orderNumber"),
                                json.getString("actionCodeDescription")).render();
                    }
                    producer.sendBody(text);
                }, fail -> {
                    producer.sendBody("Failed getPaymentStatus:" + fail.getMessage());
                });
    }

}
