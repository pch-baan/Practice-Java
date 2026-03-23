package com.practice.api.portal.v1.auth.controller;

import com.practice.api.portal.v1.auth.mapper.AuthApiMapper;
import com.practice.api.portal.v1.auth.request.LoginRequest;
import com.practice.api.portal.v1.auth.request.RefreshTokenRequest;
import com.practice.api.portal.v1.auth.request.RegisterRequest;
import com.practice.api.portal.v1.auth.response.AuthTokenResponse;
import com.practice.api.portal.v1.auth.response.RegisterResponse;
import com.practice.auth.application.port.in.ILoginUseCase;
import com.practice.auth.application.port.in.ILogoutUseCase;
import com.practice.auth.application.port.in.IRefreshTokenUseCase;
import com.practice.auth.application.port.in.IRegisterUseCase;
import com.practice.auth.application.port.in.IVerifyEmailUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IRegisterUseCase registerUseCase;
    private final IVerifyEmailUseCase verifyEmailUseCase;
    private final ILoginUseCase loginUseCase;
    private final IRefreshTokenUseCase refreshTokenUseCase;
    private final ILogoutUseCase logoutUseCase;
    private final AuthApiMapper authApiMapper;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        var command = authApiMapper.toRegisterCommand(request);
        registerUseCase.execute(command);
        var response = new RegisterResponse("Please check your email to verify your account");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthTokenResponse> verifyEmail(@RequestParam String token) {
        var dto = verifyEmailUseCase.execute(token);
        return ResponseEntity.ok(authApiMapper.toResponse(dto));
    }

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
