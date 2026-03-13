package com.practice.auth.application.dto;

public record AuthTokenDto(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInMs
) {
}
