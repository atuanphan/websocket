package com.jonet.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.jonet.demo.v1.HelloWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Configuration 
@EnableWebSocket //kích hoạt web socket trong spring
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final HelloWebSocketHandler helloWebSocketHandler;

    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(helloWebSocketHandler, "/ws")
                .setAllowedOrigins("*"); 
    }

}
