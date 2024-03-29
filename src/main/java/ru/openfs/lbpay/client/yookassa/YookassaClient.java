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
package ru.openfs.lbpay.client.yookassa;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import ru.openfs.lbpay.model.yookassa.Payment;
import ru.openfs.lbpay.model.yookassa.PaymentRequest;

@RegisterRestClient(configKey = "YooKassa")
@RegisterClientHeaders(YookassaHeaderFactory.class)
public interface YookassaClient {
    @POST
    @Path("/v3/payments")
    Payment payments(PaymentRequest request);
}
