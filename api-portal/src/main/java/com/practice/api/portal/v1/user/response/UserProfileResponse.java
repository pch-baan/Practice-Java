package com.practice.api.portal.v1.user.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    UUID userId,
    String fullName,
    String displayName,
    String avatarUrl,
    String bio,
    String phoneNumber,
    LocalDate dateOfBirth,
    String gender,
    String locale,
    String timezone,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
