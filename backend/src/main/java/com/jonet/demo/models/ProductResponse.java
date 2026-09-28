package com.jonet.demo.models;

import java.util.UUID;

public record ProductResponse(UUID id, String name, Integer quantity) {
}