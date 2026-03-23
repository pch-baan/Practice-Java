package com.practice.auth.infrastructure.scheduler;

import com.practice.auth.application.port.in.ICleanupExpiredTokensUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final ICleanupExpiredTokensUseCase cleanupExpiredTokensUseCase;

    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpiredVerificationTokens() {
        log.info("[TokenCleanup] Deleting expired email verification tokens...");
        cleanupExpiredTokensUseCase.execute();
        log.info("[TokenCleanup] Done.");
    }
}
