package com.practice.auth.domain.model;

import java.util.UUID;

public record UserCredential(
    UUID userId,
    String username,
    String passwordHash,
    String role,
    String status
) {
}
