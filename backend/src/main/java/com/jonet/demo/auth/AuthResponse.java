package com.jonet.demo.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {
}