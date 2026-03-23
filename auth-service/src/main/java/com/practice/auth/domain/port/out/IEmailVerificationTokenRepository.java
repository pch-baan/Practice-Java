package com.practice.auth.domain.port.out;

import com.practice.auth.domain.model.EmailVerificationToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IEmailVerificationTokenRepository {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteAllExpiredBefore(LocalDateTime cutoff);
}
