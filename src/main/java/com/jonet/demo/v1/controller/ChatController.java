package com.jonet.demo.v1.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {
	@MessageMapping("/sendMessage") // client gửi tới /app/sendMessage
	@SendTo("/topic/messages") // server broadcast tới /topic/messages
	public String sendMessage(String message) {
		return "Server nhận: " + message;
	}
}
