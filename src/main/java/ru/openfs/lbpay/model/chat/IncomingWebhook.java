package ru.openfs.lbpay.model.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record IncomingWebhook(
    /**
     * 	Markdown-formatted message to display in the post.
     * To trigger notifications, use @<username>, @channel, and @here like you would in other Mattermost messages.
     * req: If attachments is not set, yes
     */
    String text
) {

}
