package com.practice.user.domain.model;

import com.practice.user.domain.enums.GenderEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserProfile {

    private final UUID id;
    private final UUID userId;
    private String fullName;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private GenderEnum gender;
    private String locale;
    private String timezone;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Factory method — creates a brand-new empty profile for a user ─────────
    public static UserProfile createEmpty(UUID userId) {
        return new UserProfile(
            UUID.randomUUID(),
            userId,
            null, null, null, null, null, null, null,
            "vi-VN",
            "Asia/Ho_Chi_Minh",
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    // ── Reconstruct from persistence (no side effects — already persisted) ────
    public static UserProfile reconstruct(UUID id, UUID userId,
        String fullName, String displayName, String avatarUrl, String bio,
        String phoneNumber, LocalDate dateOfBirth, GenderEnum gender,
        String locale, String timezone,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new UserProfile(
            id, userId,
            fullName, displayName, avatarUrl, bio,
            phoneNumber, dateOfBirth, gender,
            locale, timezone,
            createdAt, updatedAt
        );
    }

    private UserProfile(UUID id, UUID userId,
        String fullName, String displayName, String avatarUrl, String bio,
        String phoneNumber, LocalDate dateOfBirth, GenderEnum gender,
        String locale, String timezone,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.fullName = fullName;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.locale = locale;
        this.timezone = timezone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Business method ───────────────────────────────────────────────────────
    public void update(String fullName, String displayName, String avatarUrl, String bio,
        String phoneNumber, LocalDate dateOfBirth, GenderEnum gender,
        String locale, String timezone) {
        this.fullName = fullName;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.locale = locale != null ? locale : this.locale;
        this.timezone = timezone != null ? timezone : this.timezone;
        this.updatedAt = LocalDateTime.now();
    }

    // ── Identity ──────────────────────────────────────────────────────────────
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "UserProfile{id=" + id + ", userId=" + userId + "}";
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID getId()              { return id; }
    public UUID getUserId()          { return userId; }
    public String getFullName()      { return fullName; }
    public String getDisplayName()   { return displayName; }
    public String getAvatarUrl()     { return avatarUrl; }
    public String getBio()           { return bio; }
    public String getPhoneNumber()   { return phoneNumber; }
    public LocalDate getDateOfBirth(){ return dateOfBirth; }
    public GenderEnum getGender()    { return gender; }
    public String getLocale()        { return locale; }
    public String getTimezone()      { return timezone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
