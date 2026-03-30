package ru.openfs.lbpay.model.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record SlashCommandResponse(
        /**
         * Markdown-formatted message to display in the post.
         * If attachments is not set, yes
         */
        String text) {
}
