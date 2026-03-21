package com.practice.api.portal.v1.auth.response;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}
