package com.practice.auth.domain.port.out;

import com.practice.auth.domain.model.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface IRefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeByTokenHash(String tokenHash);

    void revokeAllByUserId(UUID userId);
}
