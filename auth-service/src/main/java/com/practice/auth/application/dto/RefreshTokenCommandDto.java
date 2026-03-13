package com.practice.auth.application.dto;

public record RefreshTokenCommandDto(
    String rawRefreshToken
) {
}
