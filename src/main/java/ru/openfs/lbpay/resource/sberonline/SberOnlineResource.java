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
package ru.openfs.lbpay.resource.sberonline;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import ru.openfs.lbpay.exception.SberOnlineException;
import ru.openfs.lbpay.model.sberonline.SberOnlineResponse;
import ru.openfs.lbpay.model.sberonline.SberOnlineResponseType;
import ru.openfs.lbpay.service.SberOnlineService;

@Path("/pay/sber/online")
public class SberOnlineResource {
    public static final DateTimeFormatter PAY_DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH:mm:ss");
    public static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    SberOnlineService sberOnlineService;

    @Produce("direct:marshalSberOnline")
    ProducerTemplate producer;

    @ServerExceptionMapper
    public RestResponse<String> mapException(SberOnlineException x) {
        Log.error(x.getMessage());
        return RestResponse
                .ok(producer
                        .requestBody(new SberOnlineResponse.Builder()
                                .responseType(x.getResponse()).build(), String.class));
    }

    @GET
    @Produces(MediaType.APPLICATION_XML)
    @RunOnVirtualThread
    public String process(
            @QueryParam("ACTION") String action,
            @QueryParam("ACCOUNT") String account,
            @QueryParam("AMOUNT") Double amount,
            @QueryParam("PAY_ID") String payId,
            @QueryParam("PAY_DATE") String payDate) {

        if (!account.matches("\\d+$"))
            throw new SberOnlineException(SberOnlineResponseType.ACCOUNT_WRONG_FORMAT, "wrong format account: " + account);

        if (!"PAYMENT".equalsIgnoreCase(action) && !"CHECK".equalsIgnoreCase(action))
            throw new SberOnlineException(SberOnlineResponseType.WRONG_ACTION, "wrong action: " + action);

        if ("PAYMENT".equalsIgnoreCase(action)) {
            if (payId == null || payId.isBlank())
                throw new SberOnlineException(SberOnlineResponseType.WRONG_TRX_FORMAT, "required pay_id");
            if (amount == null || amount <= 0)
                throw new SberOnlineException(SberOnlineResponseType.PAY_AMOUNT_TOO_SMALL, "too small amount:" + amount);
            if (payDate == null)
                throw new SberOnlineException(SberOnlineResponseType.WRONG_FORMAT_DATE, "pay_date is required");

            return producer.requestBody(sberOnlineService.processPayment(account, payId, amount, getPayDate(payDate)),
                    String.class);
        }

        return producer.requestBody(sberOnlineService.processCheckAccount(account), String.class);
    }

    private static String getPayDate(String payDate) {
        try {
            return LocalDateTime.parse(payDate, PAY_DATE_FMT).format(BILL_DATE_FMT);
        } catch (DateTimeException ex) {
            throw new SberOnlineException(SberOnlineResponseType.WRONG_FORMAT_DATE, "bad format pay_date" + ex.getMessage());
        }
    }

}
