package com.practice.user.application.dto;

import java.util.UUID;

public record UserCredentialDto(
    UUID userId,
    String username,
    String passwordHash,
    String role,
    String status
) {
}
