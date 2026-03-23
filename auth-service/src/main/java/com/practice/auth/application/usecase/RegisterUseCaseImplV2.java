package com.practice.auth.application.usecase;

import com.practice.auth.application.dto.RegisterCommandDto;
import com.practice.auth.application.event.UserRegisteredEvent;
import com.practice.auth.application.port.in.IRegisterUseCase;
import com.practice.auth.application.port.out.ICreateUserPort;
import com.practice.auth.application.port.out.ICreateUserPort.CreatedUserResult;
import com.practice.auth.application.util.HashUtils;
import com.practice.auth.domain.model.EmailVerificationToken;
import com.practice.auth.domain.port.out.IEmailVerificationTokenRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class RegisterUseCaseImplV2 implements IRegisterUseCase {

    private final ICreateUserPort createUserPort;
    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${auth.email-verification.expiration-hours:24}")
    private int verificationExpirationHours;

    @Override
    public void execute(RegisterCommandDto command) {
        // ① tạo user qua user-service — status = PENDING
        CreatedUserResult created = createUserPort.createUser(
                command.username(), command.email(), command.password());

        // ② tạo verification token
        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = HashUtils.sha256(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(verificationExpirationHours);

        // ③ lưu token hash vào DB (không lưu raw token)
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create(created.userId(), tokenHash, expiresAt));

        // ④ publish event — email sẽ được gửi SAU KHI transaction commit
        eventPublisher.publishEvent(new UserRegisteredEvent(created.email(), rawToken));
    }

}
