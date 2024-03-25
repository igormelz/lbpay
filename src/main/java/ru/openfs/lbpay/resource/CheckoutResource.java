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

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response.Status;
import ru.openfs.lbpay.exception.CheckoutException;
import ru.openfs.lbpay.service.CheckoutService;

@Path("/pay/checkout")
public class CheckoutResource {

    @ConfigProperty(name = "account.pattern", defaultValue = "^\\d{6,7}$")
    String accountPattern;

    @ConfigProperty(name = "amount.min", defaultValue = "10")
    int amountMin;

    @ConfigProperty(name = "amount.max", defaultValue = "20000")
    int amountMax;

    @Inject
    CheckoutService checkoutService;

    @ServerExceptionMapper
    public RestResponse<String> mapException(CheckoutException x) {
        Log.errorf("checkout: %s", x.getMessage());
        return RestResponse.status(Status.BAD_REQUEST);
    }

    @GET
    @RunOnVirtualThread
    public RestResponse<Void> checkAccount(@QueryParam("uid") String account) {
        if (account != null && account.matches(accountPattern) && checkoutService.isActiveAccount(account))
            return RestResponse.noContent();
        return RestResponse.status(Status.BAD_REQUEST);
    }

    @POST
    @RunOnVirtualThread
    public RestResponse<Void> checkout(@FormParam("uid") String account, @FormParam("amount") double amount) {
        if (account != null && account.matches(accountPattern) && amount >= amountMin && amount <= amountMax) {
            return RestResponse.seeOther(URI.create(checkoutService.processCheckout(account, amount)));
        }
        return RestResponse.status(Status.BAD_REQUEST);
    }

}
