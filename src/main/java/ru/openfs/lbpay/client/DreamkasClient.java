package ru.openfs.lbpay.client;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import ru.openfs.lbpay.dto.dreamkas.Receipt;
import ru.openfs.lbpay.dto.dreamkas.Operation;

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
