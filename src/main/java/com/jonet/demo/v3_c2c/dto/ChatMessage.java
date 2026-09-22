package com.jonet.demo.v3_c2c.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessage {
    private Long id;

    private String sender;

    private String receiver;

    private String content;

    private LocalDateTime createdAt;
}
