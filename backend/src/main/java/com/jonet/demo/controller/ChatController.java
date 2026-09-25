package com.jonet.demo.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller 
public class ChatController {
    @MessageMapping("/chat.send")    
    @SendTo("/topic/messages")     // server broadcast cho tất cả subscriber
    public String greeting(String message) {
        return message;
    }
}
