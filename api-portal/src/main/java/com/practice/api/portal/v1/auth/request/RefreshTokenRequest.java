package com.practice.api.portal.v1.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token must not be blank")
        String refreshToken
) {}
