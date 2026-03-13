package com.practice.auth.application.usecase;

import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.dto.RefreshTokenCommandDto;
import com.practice.auth.application.port.in.IRefreshTokenUseCase;
import com.practice.auth.application.port.out.IJwtPort;
import com.practice.auth.domain.exception.AuthDomainException;
import com.practice.auth.domain.model.RefreshToken;
import com.practice.auth.domain.model.UserCredential;
import com.practice.auth.domain.port.out.IRefreshTokenRepository;
import com.practice.auth.domain.port.out.IUserCredentialPort;
import com.practice.auth.domain.service.AuthDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements IRefreshTokenUseCase {

    private final IRefreshTokenRepository refreshTokenRepository;
    private final IUserCredentialPort userCredentialPort;
    private final AuthDomainService authDomainService;
    private final IJwtPort jwtPort;

    @Override
    public AuthTokenDto execute(RefreshTokenCommandDto command) {
        // ① hash incoming token
        String tokenHash = sha256(command.rawRefreshToken());

        // ② find by hash
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new AuthDomainException("Invalid refresh token"));

        // ③ validate token not expired or revoked
        authDomainService.validateRefreshToken(token);

        // ④ find user
        UserCredential credential = userCredentialPort.findByUserId(token.getUserId())
            .orElseThrow(() -> new AuthDomainException("Invalid refresh token"));

        // ⑤ validate user still active
        authDomainService.validateUserCanLogin(credential);

        // ⑥ issue new access token (refresh token stays the same)
        String accessToken = jwtPort.generateAccessToken(credential.userId(), credential.role());

        return new AuthTokenDto(accessToken, null, "Bearer", jwtPort.getExpirationMs());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
