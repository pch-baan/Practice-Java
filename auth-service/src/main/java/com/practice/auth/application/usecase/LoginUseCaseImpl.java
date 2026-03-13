package com.practice.auth.application.usecase;

import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.dto.LoginCommandDto;
import com.practice.auth.application.port.in.ILoginUseCase;
import com.practice.auth.application.port.out.IJwtPort;
import com.practice.auth.domain.exception.AuthDomainException;
import com.practice.auth.domain.model.RefreshToken;
import com.practice.auth.domain.model.UserCredential;
import com.practice.auth.domain.port.out.IRefreshTokenRepository;
import com.practice.auth.domain.port.out.IUserCredentialPort;
import com.practice.auth.domain.service.AuthDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class LoginUseCaseImpl implements ILoginUseCase {

    private final IUserCredentialPort userCredentialPort;
    private final IRefreshTokenRepository refreshTokenRepository;
    private final AuthDomainService authDomainService;
    private final IJwtPort jwtPort;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.refresh-token.expiration-days:30}")
    private int refreshTokenExpirationDays;

    @Override
    public AuthTokenDto execute(LoginCommandDto command) {
        // ① find user credential — support both username and email login
        String identifier = command.username();
        UserCredential credential = userCredentialPort.findByUsername(identifier)
            .or(() -> identifier.contains("@")
                ? userCredentialPort.findByEmail(identifier)
                : Optional.empty())
            .orElseThrow(() -> new AuthDomainException("Invalid credentials"));

        // ② verify password — generic error to prevent user enumeration
        if (!passwordEncoder.matches(command.password(), credential.passwordHash())) {
            throw new AuthDomainException("Invalid credentials");
        }

        // ③ validate user status
        authDomainService.validateUserCanLogin(credential);

        // ④ generate raw refresh token (UUID — 128-bit entropy)
        String rawToken = UUID.randomUUID().toString();

        // ⑤ hash for storage (never store raw token in DB)
        String tokenHash = sha256(rawToken);

        // ⑥ create domain entity
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpirationDays);
        RefreshToken refreshToken = RefreshToken.create(credential.userId(), tokenHash, expiresAt);

        // ⑦ persist
        refreshTokenRepository.save(refreshToken);

        // ⑧ generate JWT access token
        String accessToken = jwtPort.generateAccessToken(credential.userId(), credential.role());

        // ⑨ return raw token to client (not the hash)
        return new AuthTokenDto(accessToken, rawToken, "Bearer", jwtPort.getExpirationMs());
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
