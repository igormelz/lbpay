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
import jakarta.ws.rs.BeanParam;
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

    enum Operation {
        /** операция удержания (холдирования) суммы */
        APPROVED,
        /** операция отклонения заказа по истечении его времени жизни */
        DECLINEDBYTIMEOUT,
        /** операция завершения */
        DEPOSITED,
        /** операция отмены */
        REVERSED,
        /** операция возврата */
        REFUNDED;
    }

    @ServerExceptionMapper
    public Response mapException(PaymentException x) {
        Log.errorf("sber: %s", x.getMessage());
        return Response.serverError().build();
    }

    public static class Parameters {
        /** Уникальный номер заказа в системе платёжного шлюза. */
        @RestQuery
        String mdOrder;
        /** Тип операции, о которой пришло уведомление: */
        @RestQuery
        String operation;
        /** Уникальный номер (идентификатор) заказа в системе продавца. */
        @RestQuery
        Long orderNumber;
        /** Индикатор успешности операции, указанной в параметре operation: 1 - успешно, 0 - ошибка */
        @RestQuery
        Integer status;

        @Override
        public String toString() {
            return String.format("mdOrder:%s, operation:%s, orderNumber:%d, status:%d",
                    mdOrder, operation, orderNumber, status);
        }
    }

    @GET
    public Response callback(@BeanParam Parameters parameters) {
        Log.debug(parameters);

        // validate required parameters 
        if (parameters.mdOrder == null || parameters.operation == null || parameters.orderNumber == null
                || parameters.status == null)
            throw new PaymentException("bad request");

        // validate operation 
        Operation callbackOperation;
        try {
            callbackOperation = Operation.valueOf(parameters.operation.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PaymentException("wrong operation:" + parameters.operation);
        }

        switch (callbackOperation) {
            case DEPOSITED -> {
                if (parameters.status == 1) {
                    paymentService.processPayment(parameters.orderNumber, parameters.mdOrder);
                } else {
                    Log.warnf("unsuccess operation:[%s] orderNumber:[%d] waiting for success", parameters.operation,
                            parameters.orderNumber);
                }
            }
            case REFUNDED, APPROVED -> Log.warnf("skip operation:[%s] orderNumber:[%d]", parameters.operation,
                    parameters.orderNumber);
            case DECLINEDBYTIMEOUT -> paymentService.processDecline(parameters.orderNumber);
            default -> throw new PaymentException("unprocessed operation:" + parameters.operation);
        }
        return Response.ok().build();
    }

    // @ConsumeEvent(value = "lb-payment", blocking = true)
    // public Uni<Boolean> doLbPayment(JsonObject paymentInfo) {
    //     return Uni.createFrom().item(callback(paymentInfo.getString("mdOrder"),
    //             Long.valueOf(paymentInfo.getString("orderNumber")), "deposited", 1).getStatus() == 200);
    // }

}
