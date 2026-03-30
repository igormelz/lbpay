package ru.openfs.lbpay.resource.chat;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import ru.openfs.lbpay.model.chat.SlashCommandResponse;
import ru.openfs.lbpay.service.AlertService;

@Path("/pay/chat")
public class ChatResource {

    @Inject
    AlertService alertService;

    @POST
    @Path("/wait")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public SlashCommandResponse getWaitingReceipts(
            @HeaderParam("Authorization") String authHeader,
            @FormParam("token") String token) {

        Log.info("got slash command: [wait]");

        if (token != null && authHeader.contains(token)) {
            return new SlashCommandResponse(alertService.getWaitingReceipts());
        } else {
            Log.error("bad token");
            return new SlashCommandResponse("bad token");
        }
    }

    @POST
    @Path("/reg")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public SlashCommandResponse regReceipt(
            @HeaderParam("Authorization") String authHeader,
            @FormParam("token") String token,
            @FormParam("text") String orderNumber) {

        Log.infof("got slash command: [reg] for [%s]", orderNumber);

        if (token != null && authHeader.contains(token) && !orderNumber.isBlank()) {
            return new SlashCommandResponse(alertService.doRegisterReceipt(orderNumber));
        } else {
            Log.error("bad token or blank orderNumber");
            return new SlashCommandResponse("bad token");
        }
    }
}
