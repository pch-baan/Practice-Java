package com.practice.user.domain.valueobject;

import com.practice.user.domain.exception.UserDomainException;

public final class EmailVO {

    private final String value;

    private EmailVO(String value) {
        this.value = value;
    }

    public static EmailVO of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UserDomainException("Email must not be blank");
        }
        String trimmed = raw.trim().toLowerCase();
        if (!trimmed.matches("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$")) {
            throw new UserDomainException("Email is invalid: " + raw);
        }
        return new EmailVO(trimmed);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailVO other)) return false;
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
