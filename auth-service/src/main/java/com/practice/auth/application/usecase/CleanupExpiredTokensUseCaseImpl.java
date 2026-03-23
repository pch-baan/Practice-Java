package com.practice.auth.application.usecase;

import com.practice.auth.application.port.in.ICleanupExpiredTokensUseCase;
import com.practice.auth.domain.port.out.IEmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class CleanupExpiredTokensUseCaseImpl implements ICleanupExpiredTokensUseCase {

    private final IEmailVerificationTokenRepository emailVerificationTokenRepository;

    @Override
    public void execute() {
        emailVerificationTokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
    }
}
