package com.seatsure.dto;

public record AuthResponse(
        String token, long expiresIn, UserResponse user) {
}
