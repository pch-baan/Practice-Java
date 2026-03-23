package com.practice.auth.application.usecase;

import com.practice.auth.application.dto.RegisterCommandDto;
import com.practice.auth.application.port.in.IRegisterUseCase;
import com.practice.auth.application.port.out.IEmailPort;
import com.practice.auth.domain.model.EmailVerificationToken;
import com.practice.auth.domain.port.out.ICreateUserPort;
import com.practice.auth.domain.port.out.ICreateUserPort.CreatedUserResult;
import com.practice.auth.domain.port.out.IEmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class RegisterUseCaseImpl implements IRegisterUseCase {

    private final ICreateUserPort createUserPort;
    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final IEmailPort emailPort;

    @Value("${auth.email-verification.expiration-hours:24}")
    private int verificationExpirationHours;

    @Override
    public void execute(RegisterCommandDto command) {
        // ① tạo user qua user-service — status = PENDING
        CreatedUserResult created = createUserPort.createUser(
                command.username(), command.email(), command.password());

        // ② tạo verification token
        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(verificationExpirationHours);

        // ③ lưu token hash vào DB (không lưu raw token)
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create(created.userId(), tokenHash, expiresAt));

        // ④ gửi raw token đến email user
        emailPort.sendVerificationEmail(created.email(), rawToken);
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
