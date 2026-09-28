package com.jonet.demo.models;

import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String username,
        UUID productId,
        String productName,
        Integer quantity,
        Integer remainingQuantity) {
}