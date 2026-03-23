package com.practice.auth.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class EmailVerificationToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final LocalDateTime expiresAt;
    private boolean used;
    private final LocalDateTime createdAt;

    // ── Factory method ───────────────────────────────────────────────────────
    public static EmailVerificationToken create(UUID userId, String tokenHash, LocalDateTime expiresAt) {
        return new EmailVerificationToken(null, userId, tokenHash, expiresAt, false, LocalDateTime.now());
    }

    // ── Reconstruct from persistence ─────────────────────────────────────────
    public static EmailVerificationToken reconstruct(UUID id, UUID userId, String tokenHash,
            LocalDateTime expiresAt, boolean used, LocalDateTime createdAt) {
        return new EmailVerificationToken(id, userId, tokenHash, expiresAt, used, createdAt);
    }

    private EmailVerificationToken(UUID id, UUID userId, String tokenHash,
            LocalDateTime expiresAt, boolean used, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.used = used;
        this.createdAt = createdAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }

    public void markAsUsed() {
        this.used = true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
