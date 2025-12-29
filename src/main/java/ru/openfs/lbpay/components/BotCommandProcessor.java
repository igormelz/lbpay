/*
 * Copyright 2021-2024 OpenFS.RU
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
package ru.openfs.lbpay.components;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.telegram.model.*;
import ru.openfs.lbpay.model.ReceiptCustomer;
import ru.openfs.lbpay.model.ReceiptOrder;
import ru.openfs.lbpay.model.dao.PrePaymentsDao;
import ru.openfs.lbpay.model.dreamkas.type.OperationStatus;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

@Singleton
public class BotCommandProcessor implements Processor {

    private static final String CMD_START = "/start";
    private static final String CMD_PENDING_ORDERS = "/pending";
    private static final String CMD_WAITING_RECEIPTS = "/wait";

    private static final String QUERY_PAYMENT_STATUS = "pay_status";
    private static final String QUERY_CANCEL_ORDER = "cancel_order";
    private static final String QUERY_CLEAR_MSG = "clear_msg";
    private static final String QUERY_PROCESS_PAYMENT = "process_payment";
    private static final String QUERY_REGISTER_RECEIPT = "reg_receipt";
    private static final String QUERY_REGISTER_RECEIPT_NDS = "reg_receipt_nds";
    private static final String QUERY_RECEIPT_STATUS = "receipt_status";
    private static final String QUERY_CANCEL_RECEIPT = "cancel_receipt";

    @Produce("direct:sendMessage")
    ProducerTemplate producer;

    @Inject
    EventBus eventBus;

    @Inject
    PrePaymentsDao prePaymentsDao;

    // error handler
    Consumer<Throwable> failProcessing = fail -> producer.sendBody("‼️" + fail.getMessage());

    /**
     * hanlde error message
     *
     * @param message
     */
    @ConsumeEvent(value = "notify-error", blocking = true)
    public void notifyError(String message) {
        Log.error(message);
        producer.sendBody(Templates.notifyError(message).render());
    }

    /**
     * processing camel exchange from tg
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        Object incomingMessage = exchange.getIn().getBody();
        if (incomingMessage instanceof IncomingMessage message) {
            processCommand(message.getText());
        } else if (incomingMessage instanceof IncomingCallbackQuery query) {
            processCallback(query);
        }
    }

    private void processCallback(IncomingCallbackQuery callbackQuery) {
        Log.infof("Process callback [%s]", callbackQuery.getData());

        var queryParams = Arrays.asList(callbackQuery.getData().split(":"));
        var queryType = queryParams.get(0);
        var messageId = callbackQuery.getMessage().getMessageId().intValue();
        var chatId = callbackQuery.getMessage().getChat().getId();

        // confirm to command
        OutgoingCallbackQueryMessage msg = new OutgoingCallbackQueryMessage();
        msg.setCallbackQueryId(callbackQuery.getId());
        msg.setText(queryType);
        producer.sendBody(msg);

        // process command
        switch (queryType) {
            case QUERY_PAYMENT_STATUS -> callbackPaymentStatus(queryParams.get(1), messageId);
            case QUERY_CANCEL_ORDER -> doCancelOrder(queryParams.get(1), messageId);
            case QUERY_RECEIPT_STATUS -> getOperationStatus(queryParams.get(1), messageId);
            case QUERY_REGISTER_RECEIPT -> doRegisterReceipt(queryParams.get(1), messageId, false);
            case QUERY_REGISTER_RECEIPT_NDS -> doRegisterReceipt(queryParams.get(1), messageId, true);
            case QUERY_CANCEL_RECEIPT -> doCancelRegister(queryParams.get(1), messageId);
            case QUERY_PROCESS_PAYMENT -> doProcessPayment(queryParams.get(1), queryParams.get(2), messageId);
            case QUERY_CLEAR_MSG -> producer.sendBody(new EditMessageDelete(chatId, messageId));

            default -> Log.warnf("Unknown callback: %s", callbackQuery.getData());
        }
    }

    private void processCommand(String command) {
        Log.infof("Process command [%s]", command);

        switch (command) {
            case CMD_START -> producer.sendBody("Starting LBPAY Notify Bot");
            case CMD_PENDING_ORDERS -> getPendingOrders();
            case CMD_WAITING_RECEIPTS -> getWaitingReceipts();

            default -> Log.warnf("Unknown command: %s", command);
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
    public void doRegisterReceipt(String orderNumber, int messageId, boolean useNds) {
        DreamkasOperation.findByOrderNumber(orderNumber).ifPresent(receipOperation -> {
            if (receipOperation.operationId == null || receipOperation.operationStatus == OperationStatus.ERROR) {
                Log.infof("Re-Processing orderNumber: %s", orderNumber);
                eventBus.send("register-receipt",
                        new ReceiptOrder(
                                receipOperation.amount, receipOperation.orderNumber, receipOperation.account,
                                receipOperation.externalId, new ReceiptCustomer(receipOperation.email, receipOperation.phone),
                                useNds
                        ));
            }
        });
    }

    private InlineKeyboardMarkup receiptKeyboardMarkup(DreamkasOperation receipt) {
        InlineKeyboardButton btnReg = InlineKeyboardButton.builder()
                .text("🏦 Register").callbackData(QUERY_REGISTER_RECEIPT + ":" + receipt.orderNumber).build();

        InlineKeyboardButton btnNds = InlineKeyboardButton.builder()
                .text("🏦 Register").callbackData(QUERY_REGISTER_RECEIPT_NDS + ":" + receipt.orderNumber).build();

        InlineKeyboardButton btnCancel = InlineKeyboardButton.builder()
                .text("🚮 Отмена").callbackData(QUERY_CANCEL_RECEIPT + ":" + receipt.orderNumber).build();

        InlineKeyboardButton btnStatus = InlineKeyboardButton.builder()
                .text("🔁 Status").callbackData(QUERY_RECEIPT_STATUS + ":" + receipt.operationId).build();

        if (receipt.operationId == null || receipt.operationStatus == OperationStatus.ERROR) {
            return InlineKeyboardMarkup.builder()
                    .addRow(Arrays.asList(btnReg, btnNds))
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
        var orders = prePaymentsDao.getPendingOrders();

        if (orders.isEmpty()) {
            producer.sendBody("🤷 no pending orders");
        } else {
            orders.forEach(order -> {
                OutgoingTextMessage msg = OutgoingTextMessage.builder()
                        .text(Templates.order(order).render())
                        .parseMode("HTML")
                        .replyMarkup(
                                InlineKeyboardMarkup.builder()
                                        .addRow(Arrays.asList(InlineKeyboardButton.builder()
                                                .text("🔁 Status")
                                                .callbackData(QUERY_PAYMENT_STATUS + ":" + order.orderNumber())
                                                .build()))
                                        .build())
                        .build();
                producer.sendBody(msg);
            });
        }
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
                                                        .callbackData(QUERY_PROCESS_PAYMENT + ":" +
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
                                                        .callbackData(QUERY_CANCEL_ORDER + ":" + orderNumber).build()))
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
                                                        .callbackData(QUERY_CANCEL_ORDER + ":" + orderNumber).build()))
                                                .build())
                                .build();
                    }
                    producer.sendBody(msg);
                }, failProcessing);
    }

}
