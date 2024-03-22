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
package ru.openfs.lbpay.client.dreamkas;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import ru.openfs.lbpay.model.dreamkas.Operation;
import ru.openfs.lbpay.model.dreamkas.Receipt;

@RegisterRestClient(configKey = "Dreamkas")
public interface DreamkasClient {
    @POST
    @Path("/api/receipts")
    @ClientHeaderParam(name = "Authorization", value = "Bearer ${dreamkas.token}")
    Operation register(Receipt receipt);

    @GET
    @Path("/api/operations/{operationId}")
    @ClientHeaderParam(name = "Authorization", value = "Bearer ${dreamkas.token}")
    Operation getOperation(@PathParam("operationId") String operationId);

}
