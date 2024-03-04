package ru.openfs.lbpay.bot;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.telegram.model.EditMessageDelete;
import org.apache.camel.component.telegram.model.EditMessageTextMessage;
import org.apache.camel.component.telegram.model.IncomingCallbackQuery;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;
import org.apache.camel.component.telegram.model.OutgoingCallbackQueryMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import ru.openfs.lbpay.dto.dreamkas.type.OperationStatus;
import ru.openfs.lbpay.mapper.ReceiptOrderBuilder;
import ru.openfs.lbpay.model.AuditRecord;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

@Singleton
public class BotCommandProcessor implements Processor {

    private static final String CMD_START = "/start";
    private static final String CMD_PENDING_ORDERS = "/pending";
    private static final String CMD_WAITING_RECEIPTS = "/wait";
    private static final String CMD_PAYMENT_STATUS = "pay_status";
    private static final String CMD_CANCEL_ORDER = "cancel_order";
    private static final String CMD_CLEAR_MSG = "clear_msg";
    private static final String CMD_PROCESS_PAYMENT = "process_payment";
    private static final String CMD_REGISTER_RECEIPT = "reg_receipt";
    private static final String CMD_RECEIPT_STATUS = "receipt_status";
    private static final String CMD_CANCEL_RECEIPT = "cancel_receipt";

    @Produce("direct:sendMessage")
    ProducerTemplate producer;

    @Inject
    EventBus eventBus;

    // error handler
    Consumer<Throwable> failProcessing = fail -> producer.sendBody("‼️" + fail.getMessage());

    @ConsumeEvent(value = "notify-error", blocking = true)
    public void notifyError(String message) {
        Log.error(message);
        producer.sendBody(Templates.notifyError(message).render());
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object incomingMessage = exchange.getIn().getBody();
        if (incomingMessage instanceof IncomingMessage) {
            processCommand(((IncomingMessage) incomingMessage).getText());
        } else if (incomingMessage instanceof IncomingCallbackQuery) {
            processCallback((IncomingCallbackQuery) incomingMessage);
        }
    }

    private void processCallback(IncomingCallbackQuery callbackQuery) {
        Log.infof("Process callback [%s]", callbackQuery.getData());

        // parse command
        String[] command = callbackQuery.getData().split(":");
        // get messageId
        int messageId = callbackQuery.getMessage().getMessageId().intValue();
        // get chatId
        String chatId = callbackQuery.getMessage().getChat().getId();

        // replay confirm to command
        OutgoingCallbackQueryMessage msg = new OutgoingCallbackQueryMessage();
        msg.setCallbackQueryId(callbackQuery.getId());
        msg.setText(command[0]);
        producer.sendBody(msg);

        // process command
        switch (command[0]) {
            case CMD_PAYMENT_STATUS:
                callbackPaymentStatus(command[1], messageId);
                break;
            case CMD_CANCEL_ORDER:
                doCancelOrder(command[1], messageId);
                break;
            case CMD_RECEIPT_STATUS:
                getOperationStatus(command[1], messageId);
                break;
            case CMD_REGISTER_RECEIPT:
                doRegisterReceipt(command[1], messageId);
                break;
            case CMD_CANCEL_RECEIPT:
                doCancelRegister(command[1], messageId);
                break;
            case CMD_PROCESS_PAYMENT:
                doProcessPayment(command[1], command[2], messageId);
                break;
            case CMD_CLEAR_MSG:
                producer.sendBody(new EditMessageDelete(chatId, messageId));
                break;
            default:
                Log.warnf("Unknown callback: %s", callbackQuery.getData());
        }
    }

    private void processCommand(String command) {
        Log.infof("Process command [%s]", command);

        switch (command) {
            case CMD_START:
                producer.sendBody("Starting LBPAY Notify Bot");
                break;
            case CMD_PENDING_ORDERS:
                getPendingOrders();
                break;
            case CMD_WAITING_RECEIPTS:
                getWaitingReceipts();
                break;
            default:
                Log.warnf("Unknown command: %s", command);
        }
    }

    private void doProcessPayment(String orderNumber, String mdOrder, int messageId) {
        eventBus.request("lb-payment", new JsonObject().put("orderNumber", orderNumber).put("mdOrder", mdOrder)).subscribe()
                .with(ok -> {
                    if ((Boolean) ok.body())
                        producer.sendBody(EditMessageTextMessage.builder()
                                .messageId(messageId)
                                .text("💳 Заказ #" + orderNumber + " 🆗 оплачен")
                                .build());
                    else
                        producer.sendBody("💳 Заказ #" + orderNumber + " ❓не найден");
                }, failProcessing);
    }

    @Transactional
    public void doRegisterReceipt(String orderNumber, int messageId) {
        DreamkasOperation.findByOrderNumber(orderNumber).ifPresent(receipOperation -> {
            if (receipOperation.operationId == null) {
                Log.infof("Re-Processing not registered orderNumber: %s", orderNumber);
                eventBus.send("register-receipt", ReceiptOrderBuilder.createReceiptOrderFromOperation(receipOperation));
            } else if (receipOperation.operationStatus == OperationStatus.ERROR) {
                Log.infof("Re-Processing error orderNumber: %s", orderNumber);
                eventBus.send("register-receipt", ReceiptOrderBuilder.createReceiptOrderFromOperation(receipOperation));
            }
        });
    }

    private InlineKeyboardMarkup receiptKeyboardMarkup(DreamkasOperation receipt) {
        InlineKeyboardButton btnReg = InlineKeyboardButton.builder()
                .text("🏦 Register").callbackData(CMD_REGISTER_RECEIPT + ":" + receipt.orderNumber).build();

        InlineKeyboardButton btnCancel = InlineKeyboardButton.builder()
                .text("🚮 Отмена").callbackData(CMD_CANCEL_RECEIPT + ":" + receipt.orderNumber).build();

        InlineKeyboardButton btnStatus = InlineKeyboardButton.builder()
                .text("🔁 Status").callbackData(CMD_RECEIPT_STATUS + ":" + receipt.operationId).build();

        if (receipt.operationId == null || receipt.operationStatus == OperationStatus.ERROR) {
            return InlineKeyboardMarkup.builder()
                    .addRow(Arrays.asList(btnReg, btnStatus, btnCancel))
                    .build();
        } else if (receipt.operationStatus == OperationStatus.SUCCESS) {
            return InlineKeyboardMarkup.builder()
                    .addRow(Arrays.asList(btnCancel))
                    .build();
        } else {
            return InlineKeyboardMarkup.builder()
                    .addRow(Arrays.asList(btnStatus))
                    .build();
        }
    }

    private void doCancelRegister(String orderNumber, int messageId) {
        // audit.deleteOperationByOrderNumber(orderNumber).subscribe().with(
        //         deleted -> {
        //             if (deleted)
        //                 producer.sendBody(EditMessageTextMessage.builder()
        //                         .messageId(messageId)
        //                         .text("🚮 Прекращена регистрация чека \n 💳 Заказ #" + orderNumber)
        //                         .build());
        //             else
        //                 producer.sendBody("💳 Заказ #" + orderNumber + " ❓не найден");
        //         },
        //         failProcessing);
    }

    private void getOperationStatus(String operation, int messageId) {
        // bus.request("dk-register-status", operation).subscribe().with(
        //         status -> {
        //             var operation = status.body()
        //             audit.setOperation(opJson);
        //             audit.findById(opJson.getString("externalId")).subscribe().with(
        //                     receipt -> {
        //                         producer.sendBody(EditMessageTextMessage.builder().messageId(messageId)
        //                                 .text(Templates.receipt(receipt).render()).parseMode("HTML")
        //                                 .replyMarkup(receiptKeyboardMarkup(receipt)).build());
        //                     });
        //         },
        //         failProcessing);
    }

    @Transactional
    public void getWaitingReceipts() {
        List<DreamkasOperation> receipts = DreamkasOperation.listAll(Sort.by("createAt"));
        if (receipts.isEmpty())
            producer.sendBody("🆗 no waiting receipts");
        else
            receipts.forEach(receipt -> {
                producer.sendBody(OutgoingTextMessage.builder().text(Templates.receiptOperationStatus(receipt).render())
                        .parseMode("HTML").replyMarkup(receiptKeyboardMarkup(receipt)).build());
            });
    }

    private void doCancelOrder(String orderNumber, int messageId) {
        // audit.clearOrder(orderNumber).subscribe().with(
        //         deleted -> {
        //             if (deleted)
        //                 producer.sendBody(EditMessageTextMessage.builder()
        //                         .messageId(messageId)
        //                         .text("💳 Заказ #" + orderNumber + " 🚮 отменен")
        //                         // .replyMarkup(KM_CLEAR)
        //                         .build());
        //             else
        //                 producer.sendBody("💳 Заказ #" + orderNumber + " ❓не найден");
        //         },
        //         failProcessing);
    }

    private void getPendingOrders() {
        // audit.getPendingOrders().collect().asList().subscribe().with(
        //         orders -> {
        //             if (orders.size() == 0) {
        //                 producer.sendBody("🤷 no pending orders");
        //             } else {
        //                 orders.forEach(order -> {
        //                     OutgoingTextMessage msg = OutgoingTextMessage.builder()
        //                             .text(Templates.order(order).render())
        //                             .parseMode("HTML")
        //                             .replyMarkup(
        //                                     InlineKeyboardMarkup.builder()
        //                                             .addRow(Arrays.asList(InlineKeyboardButton.builder()
        //                                                     .text("🔁 Status")
        //                                                     .callbackData(CMD_PAYMENT_STATUS + ":" + order.orderNumber())
        //                                                     .build()))
        //                                             .build())
        //                             .build();
        //                     producer.sendBody(msg);
        //                 });
        //             }
        //         },
        //         failProcessing);
    }

    private void callbackPaymentStatus(String orderNumber, int messageId) {
        eventBus.request("sber-payment-status", orderNumber).subscribe().with(
                status -> {
                    EditMessageTextMessage msg;
                    // process answer as json
                    JsonObject json = JsonObject.mapFrom(status.body());
                    if (json.getString("errorCode").equalsIgnoreCase("0") && json.getInteger("actionCode") == 0) {
                        msg = EditMessageTextMessage.builder()
                                .messageId(messageId)
                                .text(Templates.pay_status("🆗",
                                        json.getJsonObject("paymentAmountInfo").getString("paymentState"),
                                        json.getString("orderNumber"),
                                        json.getString("errorMessage")).render())
                                .replyMarkup(
                                        InlineKeyboardMarkup
                                                .builder()
                                                .addRow(Arrays.asList(InlineKeyboardButton.builder().text("💳 payment")
                                                        .callbackData(CMD_PROCESS_PAYMENT + ":" +
                                                                      orderNumber + ":" +
                                                                      json.getJsonArray("attributes").getJsonObject(0)
                                                                              .getString("value"))
                                                        .build()))
                                                .build())
                                .build();
                    } else if (json.getString("errorCode").equalsIgnoreCase("6")) {
                        msg = EditMessageTextMessage.builder()
                                .messageId(messageId)
                                .text(Templates.pay_status("❓",
                                        "NOT FOUND",
                                        orderNumber,
                                        json.getString("errorMessage")).render())
                                .replyMarkup(
                                        InlineKeyboardMarkup
                                                .builder()
                                                .addRow(Arrays.asList(InlineKeyboardButton.builder().text("🚮 cancel")
                                                        .callbackData(CMD_CANCEL_ORDER + ":" + orderNumber).build()))
                                                .build())
                                .build();
                    } else {
                        msg = EditMessageTextMessage.builder()
                                .messageId(messageId)
                                .text(Templates.pay_status("❌",
                                        json.getJsonObject("paymentAmountInfo").getString("paymentState"),
                                        json.getString("orderNumber"),
                                        json.getString("actionCodeDescription")).render())
                                .replyMarkup(
                                        InlineKeyboardMarkup
                                                .builder()
                                                .addRow(Arrays.asList(InlineKeyboardButton.builder().text("🚮 cancel")
                                                        .callbackData(CMD_CANCEL_ORDER + ":" + orderNumber).build()))
                                                .build())
                                .build();
                    }
                    producer.sendBody(msg);
                }, failProcessing);
    }

}
