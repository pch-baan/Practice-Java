package com.practice.auth.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final LocalDateTime expiresAt;
    private boolean revoked;
    private final LocalDateTime createdAt;

    // ── Factory method — create a new token (not yet persisted) ─────────────
    public static RefreshToken create(UUID userId, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(
            null,
            userId,
            tokenHash,
            expiresAt,
            false,
            LocalDateTime.now()
        );
    }

    // ── Reconstruct from persistence ─────────────────────────────────────────
    public static RefreshToken reconstruct(UUID id, UUID userId, String tokenHash,
        LocalDateTime expiresAt, boolean revoked,
        LocalDateTime createdAt) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revoked, createdAt);
    }

    private RefreshToken(UUID id, UUID userId, String tokenHash,
        LocalDateTime expiresAt, boolean revoked, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.createdAt = createdAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    public void revoke() {
        this.revoked = true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
