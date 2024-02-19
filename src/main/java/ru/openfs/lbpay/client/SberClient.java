package ru.openfs.lbpay.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedMap;

@RegisterRestClient(configKey = "SberClient")
public interface SberClient {

    @POST
    @Path("/payment/rest/register.do")
    String register(@RestQuery MultivaluedMap<String, String> request);

    @POST
    @Path("/payment/rest/getOrderStatusExtended.do")
    String getOrderStatus(@RestQuery String userName, @RestQuery String password, @RestQuery String orderNumber);

}
