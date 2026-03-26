package com.practice.auth.domain.port.out;

import com.practice.auth.domain.model.EmailVerificationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IEmailVerificationTokenRepository {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteById(UUID id);

    List<UUID> findUserIdsByExpiredBefore(LocalDateTime cutoff);

    void deleteAllExpiredBefore(LocalDateTime cutoff);
}
