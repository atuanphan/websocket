package com.jonet.demo.v2_chat_room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessage {
	public enum MessageType {
        CHAT, JOIN, LEAVE
    }

    private MessageType type;
    private String sender;
    private String content;

    // Constructors
    public ChatMessage() {}

    public ChatMessage(MessageType type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

}
