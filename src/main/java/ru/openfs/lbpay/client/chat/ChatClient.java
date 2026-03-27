package ru.openfs.lbpay.client.chat;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import ru.openfs.lbpay.model.chat.IncomingWebhook;

@RegisterRestClient(configKey = "Chat")
public interface ChatClient {
    @POST
    @Path("/hooks/{id}")
    String post(@PathParam("id") String id, IncomingWebhook webhook);
}
