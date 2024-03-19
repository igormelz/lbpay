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
package ru.openfs.lbpay.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import ru.openfs.lbpay.exception.PaymentException;
import ru.openfs.lbpay.service.PaymentService;

import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.logging.Log;

@Path("/pay/sber/callback")
public class SberWebhookResource {

    @Inject
    PaymentService paymentService;

    @ServerExceptionMapper
    public Response mapException(PaymentException x) {
        Log.errorf("sber: %s", x.getMessage());
        return Response.serverError().build();
    }

    /**
     * processing sberbank webhook callback
     * 
     * @param  mdOrder     Уникальный номер заказа в системе платёжного шлюза.
     * @param  operation   Тип операции, о которой пришло уведомление: approved, reversed, refunded, deposited,
     *                     declinedByTimeout
     * @param  orderNumber Уникальный номер (идентификатор) заказа в системе продавца
     * @param  status      Индикатор успешности операции, указанной в параметре operation: 1 - успешно, 0 - ошибка
     * @return             http_status 200 if OK, otherwise 500
     */
    @GET
    public Response callback(
            @RestQuery String mdOrder,
            @RestQuery String operation,
            @RestQuery Long orderNumber,
            @RestQuery Integer status) {

        Log.debugf("sber callback: mdOrder:%s, operation:%s, orderNumber:%d, status:%d",
                mdOrder, operation, orderNumber, status);

        // validate parameters 
        if (mdOrder == null || operation == null || orderNumber == null || status == null)
            throw new PaymentException("bad request");

        // process operation
        switch (operation.toLowerCase()) {
            case "deposited" -> {
                if (status == 1) {
                    paymentService.processPayment(orderNumber, mdOrder);
                } else {
                    Log.warnf("unsuccess %s for [%d] waiting for success", operation, orderNumber);
                }
            }
            case "refunded", "approved" -> Log.warnf("skip [%s] for [%d]", operation, orderNumber);
            case "declinedbytimeout" -> paymentService.processDecline(orderNumber);
            default -> throw new PaymentException("unprocessed operation:" + operation);
        }
        return Response.ok().build();
    }

    // @ConsumeEvent(value = "lb-payment", blocking = true)
    // public Uni<Boolean> doLbPayment(JsonObject paymentInfo) {
    //     return Uni.createFrom().item(callback(paymentInfo.getString("mdOrder"),
    //             Long.valueOf(paymentInfo.getString("orderNumber")), "deposited", 1).getStatus() == 200);
    // }

}
