package com.practice.api.portal.v1.auth.mapper;

import com.practice.api.portal.v1.auth.request.LoginRequest;
import com.practice.api.portal.v1.auth.request.RefreshTokenRequest;
import com.practice.api.portal.v1.auth.request.RegisterRequest;
import com.practice.api.portal.v1.auth.response.AuthTokenResponse;
import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.dto.LoginCommandDto;
import com.practice.auth.application.dto.RefreshTokenCommandDto;
import com.practice.auth.application.dto.RegisterCommandDto;
import org.springframework.stereotype.Component;

@Component
public class AuthApiMapper {

    public RegisterCommandDto toRegisterCommand(RegisterRequest request) {
        return new RegisterCommandDto(request.username(), request.email(), request.password());
    }

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
