package com.practice.api.portal.auth.controller;

import com.practice.api.portal.auth.mapper.AuthApiMapper;
import com.practice.api.portal.auth.request.LoginRequest;
import com.practice.api.portal.auth.request.RefreshTokenRequest;
import com.practice.api.portal.auth.response.AuthTokenResponse;
import com.practice.auth.application.port.in.ILoginUseCase;
import com.practice.auth.application.port.in.ILogoutUseCase;
import com.practice.auth.application.port.in.IRefreshTokenUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ILoginUseCase loginUseCase;
    private final IRefreshTokenUseCase refreshTokenUseCase;
    private final ILogoutUseCase logoutUseCase;
    private final AuthApiMapper authApiMapper;

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var command = authApiMapper.toLoginCommand(request);
        var dto = loginUseCase.execute(command);
        return ResponseEntity.ok(authApiMapper.toResponse(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
        @Valid @RequestBody RefreshTokenRequest request) {
        var command = authApiMapper.toRefreshCommand(request);
        var dto = refreshTokenUseCase.execute(command);
        return ResponseEntity.ok(authApiMapper.toResponse(dto));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        logoutUseCase.execute(request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
