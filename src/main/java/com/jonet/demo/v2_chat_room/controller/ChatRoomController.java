package com.jonet.demo.v2_chat_room.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import com.jonet.demo.v2_chat_room.dto.ChatMessage;

@Controller
@MessageMapping("/room")
public class ChatRoomController {
	
	@MessageMapping("/chat-send")
	@SendTo("/topic/public")
	public ChatMessage send(ChatMessage chatMessage) {
		return chatMessage;
	}
	
	@MessageMapping("/chat-join")
	@SendTo("/topic/public")
	public ChatMessage join(ChatMessage chatMessage) {
		chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setContent(chatMessage.getSender() + " đã tham gia phòng chat");
        return chatMessage;
	}
}
