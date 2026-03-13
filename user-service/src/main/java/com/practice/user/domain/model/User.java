package com.practice.user.domain.model;

import com.practice.user.domain.enums.UserRoleEnum;
import com.practice.user.domain.enums.UserStatusEnum;
import com.practice.user.domain.exception.UserDomainException;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.PasswordHashVO;
import com.practice.user.domain.valueobject.UsernameVO;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {

    private final UUID id;
    private UsernameVO username;
    private EmailVO email;
    private PasswordHashVO passwordHash;
    private UserRoleEnum role;
    private UserStatusEnum status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Factory method — the only way to create a new User ──────────────────
    public static User create(String username, String email, String passwordHash) {
        return new User(
            UUID.randomUUID(),
            UsernameVO.of(username),
            EmailVO.of(email),
            PasswordHashVO.of(passwordHash),
            UserRoleEnum.USER,
            UserStatusEnum.ACTIVE,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    // ── Reconstruct from persistence (no validation — already persisted) ─────
    public static User reconstruct(UUID id, String username, String email,
        String passwordHash, UserRoleEnum role,
        UserStatusEnum status, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
        return new User(
            id,
            UsernameVO.of(username),
            EmailVO.of(email),
            PasswordHashVO.of(passwordHash),
            role, status, createdAt, updatedAt
        );
    }

    private User(UUID id, UsernameVO username, EmailVO email, PasswordHashVO passwordHash,
        UserRoleEnum role, UserStatusEnum status,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Business methods ─────────────────────────────────────────────────────
    public void updateEmail(String newEmail) {
        this.email = EmailVO.of(newEmail);
        this.updatedAt = LocalDateTime.now();
    }

    public void updateUsername(String newUsername) {
        this.username = UsernameVO.of(newUsername);
        this.updatedAt = LocalDateTime.now();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = PasswordHashVO.of(newPasswordHash);
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        if (this.status == UserStatusEnum.ACTIVE) {
            throw new UserDomainException("User is already active");
        }
        this.status = UserStatusEnum.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        if (this.status == UserStatusEnum.INACTIVE) {
            throw new UserDomainException("User is already inactive");
        }
        this.status = UserStatusEnum.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatusEnum.ACTIVE;
    }

    // ── Identity: equals/hashCode based on ID only ───────────────────────────
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', email='" + email + "', status=" + status + "}";
    }

    // ── Getters (no setters — state changes go through business methods) ──────
    public UUID getId() {
        return id;
    }

    public UsernameVO getUsername() {
        return username;
    }

    public EmailVO getEmail() {
        return email;
    }

    public PasswordHashVO getPasswordHash() {
        return passwordHash;
    }

    public UserRoleEnum getRole() {
        return role;
    }

    public UserStatusEnum getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
