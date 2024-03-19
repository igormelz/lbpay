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
package ru.openfs.lbpay.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.logging.Log;
import ru.openfs.lbpay.dto.sberonline.Message;
import ru.openfs.lbpay.exception.SberOnlineException;
import ru.openfs.lbpay.mapper.SberOnlineMapper;
import ru.openfs.lbpay.service.SberOnlineService;

@Path("/pay/sber/online")
public class SberOnlineResource {

    private SberOnlineService sberOnlineService;

    @Inject
    public SberOnlineResource(SberOnlineService sberOnlineService) {
        this.sberOnlineService = sberOnlineService;
    }

    @Produce("direct:marshalSberOnline")
    ProducerTemplate producer;

    @ServerExceptionMapper
    public RestResponse<String> mapException(SberOnlineException x) {
        Log.error(x.getMessage());
        return RestResponse.ok(producer.requestBody(new Message(x.getResponse()), String.class));
    }

    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String process(
            @QueryParam("ACTION") String action,
            @QueryParam("ACCOUNT") String account,
            @QueryParam("AMOUNT") Double amount,
            @QueryParam("PAY_ID") String payId,
            @QueryParam("PAY_DATE") String payDate) {

        var request = SberOnlineMapper.validateRequest(action, account, amount, payId, payDate);
        
        var response = switch (request.operation()) {
            case CHECK -> SberOnlineMapper.fromCheckResponse(sberOnlineService.processCheckAccount(request.account()));
            default -> SberOnlineMapper.fromPaymentResponse(sberOnlineService.processPayment(request));
        };

        return producer.requestBody(response, String.class);
    }


}
