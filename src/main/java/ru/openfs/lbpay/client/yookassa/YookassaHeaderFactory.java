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
