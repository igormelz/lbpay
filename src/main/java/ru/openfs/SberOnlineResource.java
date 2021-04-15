package ru.openfs;

import java.util.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import ru.openfs.model.SberOnlineMessage;

@Path("/sber/online")
public class SberOnlineResource {
    private static final Logger log = Logger.getLogger(SberOnlineResource.class.getName());

    @Inject
    EventBus bus;

    @GET
    @Produces(MediaType.APPLICATION_XML)
    public SberOnlineMessage hello() {
        JsonObject attempt = new JsonObject();
        attempt.put("amount", 10.1234);
        attempt.put("mdOrder", "47398-234324-234234-qwqweq");
        attempt.put("orderNumber", 354547474);
        attempt.put("email", "echo@com.co");
        attempt.put("phone", "727272727");
        //log.info("Start");
        bus.sendAndForget("register", attempt);
        var answer = new SberOnlineMessage();
        answer.CODE = 0;
        answer.MESSAGE = "all right Дорогой";
        return answer;
    }
}