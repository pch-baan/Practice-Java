package com.practice.user.domain.valueobject;

import com.practice.user.domain.exception.UserDomainException;

public final class UsernameVO {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 50;

    private final String value;

    private UsernameVO(String value) {
        this.value = value;
    }

    public static UsernameVO of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UserDomainException("Username must not be blank");
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new UserDomainException(
                "Username must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (!trimmed.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new UserDomainException(
                "Username may only contain letters, digits, underscores, dots, and hyphens");
        }
        return new UsernameVO(trimmed);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsernameVO other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
