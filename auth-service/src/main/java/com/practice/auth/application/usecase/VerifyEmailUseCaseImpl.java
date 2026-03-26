package com.practice.auth.application.usecase;

import com.practice.auth.application.dto.AuthTokenDto;
import com.practice.auth.application.port.in.IVerifyEmailUseCase;
import com.practice.auth.application.port.out.IJwtPort;
import com.practice.auth.domain.exception.AuthDomainException;
import com.practice.auth.domain.model.EmailVerificationToken;
import com.practice.auth.domain.model.RefreshToken;
import com.practice.auth.application.port.out.IActivateUserPort;
import com.practice.auth.domain.port.out.IEmailVerificationTokenRepository;
import com.practice.auth.domain.port.out.IRefreshTokenRepository;
import com.practice.auth.domain.service.AuthDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.practice.auth.application.util.HashUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class VerifyEmailUseCaseImpl implements IVerifyEmailUseCase {

    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final IActivateUserPort activateUserPort;
    private final IRefreshTokenRepository refreshTokenRepository;
    private final AuthDomainService authDomainService;
    private final IJwtPort jwtPort;

    @Value("${auth.refresh-token.expiration-days:30}")
    private int refreshTokenExpirationDays;

    @Override
    public AuthTokenDto execute(String rawToken) {
        // ① tìm token theo hash
        String tokenHash = HashUtils.sha256(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthDomainException("Invalid verification token"));

        // ② validate (chưa dùng, chưa hết hạn)
        authDomainService.validateVerificationToken(token);

        // ③ xóa token — đã dùng xong, không cần giữ lại
        emailVerificationTokenRepository.deleteById(token.getId());

        // ④ kích hoạt user — status PENDING → ACTIVE
        activateUserPort.activate(token.getUserId());

        // ⑤ phát JWT + refresh token (giống login)
        String rawRefreshToken = UUID.randomUUID().toString();
        String refreshTokenHash = HashUtils.sha256(rawRefreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpirationDays);
        refreshTokenRepository.save(RefreshToken.create(token.getUserId(), refreshTokenHash, expiresAt));

        String accessToken = jwtPort.generateAccessToken(token.getUserId(), resolveRole(token.getUserId()));

        return new AuthTokenDto(accessToken, rawRefreshToken, "Bearer", jwtPort.getExpirationMs());
    }

    // User mới đăng ký luôn có role USER — không cần query lại DB
    private String resolveRole(UUID userId) {
        return "USER";
    }

}
