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

import java.util.Base64;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

@ApplicationScoped
public class YookassaHeaderFactory implements ClientHeadersFactory {
    @ConfigProperty(name = "yookassa.id", defaultValue = "store-id")
    String storeId;
    @ConfigProperty(name = "yookassa.key", defaultValue = "store-key")
    String storeKey;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> arg0, MultivaluedMap<String, String> arg1) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        result.add("Idempotence-Key", UUID.randomUUID().toString());
        result.add("Authorization",
                "Basic " + Base64.getEncoder().encodeToString((storeId + ":" + storeKey).getBytes()));
        return result;
    }

}
