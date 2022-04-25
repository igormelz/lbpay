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
        // String chatId =
        // exchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID, String.class);
        Log.infof("body:[%s]", body);
        // parse command
        String command = body.replaceAll("^/([a-zA-Z_]+)_(.*)$", "$1");
        String arg = body.replaceAll("^/([a-zA-Z_]+)_(.*)$", "$2");
        Log.infof("Parsed command:%s args:%s", command, arg);

        // process command
        switch (command) {
            case "/pending":
                getPendingOrders();
                break;
            case "payment_status":
                getOrderPaymentStatus(arg);
                break;
            case "cancel_order":
                doCancelOrder(arg);
                break;
            case "/wait":
                getWaitReceipts();
                break;
            case "register_status":
                getOperationStatus(arg);
                break;
            case "cancel_register":
                doCancelRegister(arg);
                break;
            case "register":
                doRegisterOrder(arg);
                break;
            default:
                Log.warnf("Unknown command: %s", command);
        }
    }

    private void doRegisterOrder(String orderNumber) {
        audit.findByOrderNumber(orderNumber).subscribe().with(
                order -> {
                    if (order == null) {
                        producer.sendBody("❓не найден заказ:" + orderNumber);

                    } else if (order.operId == null) {
                        Log.infof("re-processing not registered orderNumber: %s", orderNumber);
                        bus.send("receipt-sale", JsonObject.mapFrom(order));
                        producer.sendBody("✅ отправлен запрос на регистрацию заказа:" + orderNumber);

                    } else if (order.status.equalsIgnoreCase("ERROR")) {
                        Log.warnf("re-processing error orderNumber: %s", order.orderNumber);
                        bus.send("receipt-sale", JsonObject.mapFrom(order));
                        producer.sendBody("✅ отправлен запрос на регистрацию заказа:" + orderNumber);

                    } else {
                        producer.sendBody("❓не определен статус заказа:" + orderNumber);
                    }
                },
                fail -> {
                    producer.sendBody("‼️ Failed registerOrder:" + fail.getMessage());
                });
    }

    private void doCancelRegister(String orderNumber) {
        audit.deleteOperation(orderNumber).subscribe().with(
                deleted -> {
                    if (deleted)
                        producer.sendBody("💳 Заказ #" + orderNumber + " 🚮 отменена регистрации чека");
                    else
                        producer.sendBody("💳 Заказ #" + orderNumber + " ❓не найден");
                },
                fail -> {
                    producer.sendBody("‼️ Failed cancelRegister:" + fail.getMessage());
                });
    }

    private void getOperationStatus(String operation) {
        producer.sendBody("Get register status ...");
        bus.request("dk-register-status", operation).subscribe().with(
                status -> {
                    String text = status.body().toString();
                    producer.sendBody(text);
                },
                fail -> {
                    producer.sendBody("‼️ Failed getRegisterStatus:" + fail.getMessage());
                });
    }

    private void getWaitReceipts() {
        audit.findAll().collect().asList().subscribe().with(response -> {
            if (response.size() == 0) {
                producer.sendBody("🆗 no waiting receipts");
            } else {
                response.forEach(
                        receipt -> producer.sendBody(Templates.receipt(receipt).render()));
            }
        }, fail -> {
            producer.sendBody("‼️ Failed get wait receipts:" + fail.getMessage());
        });
    }

    private void doCancelOrder(String orderNumber) {
        audit.clearOrder(orderNumber).subscribe().with(
                deleted -> {
                    if (deleted)
                        producer.sendBody("💳 Заказ #" + orderNumber + " 🚮 отменен");
                    else
                        producer.sendBody("💳 Заказ #" + orderNumber + " ❓не найден");
                },
                fail -> {
                    producer.sendBody("‼️ Failed cancel:" + fail.getMessage());
                });
    }

    private void getPendingOrders() {
        audit.getPendingOrders().collect().asList().subscribe().with(
                orders -> {
                    if (orders.size() == 0) {
                        producer.sendBody("🤷 no pending orders");
                    } else {
                        orders.forEach(order -> producer.sendBody(Templates.order(order).render()));
                    }
                },
                fail -> {
                    producer.sendBody("‼️ Failed getPendingOrders:" + fail.getMessage());
                });
    }

    private void getOrderPaymentStatus(String orderNumber) {
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
                    producer.sendBody("‼️ Failed getPaymentStatus:" + fail.getMessage());
                });
    }

}
