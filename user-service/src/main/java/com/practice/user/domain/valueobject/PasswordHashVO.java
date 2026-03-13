package com.practice.user.domain.valueobject;

import com.practice.user.domain.exception.UserDomainException;

public final class PasswordHashVO {

    private final String value;

    private PasswordHashVO(String value) {
        this.value = value;
    }

    public static PasswordHashVO of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UserDomainException("Password hash must not be blank");
        }
        return new PasswordHashVO(raw);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordHashVO other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "[PROTECTED]";
    }
}
