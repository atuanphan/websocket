package com.jonet.demo.models;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data 
@Builder 
public class StockUpdateMessage {
    private UUID productId;
    private Integer remainingQuantity;
}