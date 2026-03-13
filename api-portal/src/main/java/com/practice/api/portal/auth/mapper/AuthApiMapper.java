package com.practice.api.portal.auth.mapper;

import com.practice.api.portal.auth.request.LoginRequest;
import com.practice.api.portal.auth.request.RefreshTokenRequest;
import com.practice.api.portal.auth.response.AuthTokenResponse;
import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.dto.LoginCommandDto;
import com.practice.auth.application.dto.RefreshTokenCommandDto;
import org.springframework.stereotype.Component;

@Component
public class AuthApiMapper {

    public LoginCommandDto toLoginCommand(LoginRequest request) {
        return new LoginCommandDto(request.username(), request.password());
    }

    public RefreshTokenCommandDto toRefreshCommand(RefreshTokenRequest request) {
        return new RefreshTokenCommandDto(request.refreshToken());
    }

    public AuthTokenResponse toResponse(AuthTokenDto dto) {
        return new AuthTokenResponse(
            dto.accessToken(),
            dto.refreshToken(),
            dto.tokenType(),
            dto.expiresInMs()
        );
    }
}
