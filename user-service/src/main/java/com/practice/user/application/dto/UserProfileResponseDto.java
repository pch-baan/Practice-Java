package com.practice.user.application.dto;

import com.practice.user.domain.enums.GenderEnum;
import com.practice.user.domain.model.UserProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponseDto(
    UUID id,
    UUID userId,
    String fullName,
    String displayName,
    String avatarUrl,
    String bio,
    String phoneNumber,
    LocalDate dateOfBirth,
    GenderEnum gender,
    String locale,
    String timezone,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserProfileResponseDto from(UserProfile profile) {
        return new UserProfileResponseDto(
            profile.getId(),
            profile.getUserId(),
            profile.getFullName(),
            profile.getDisplayName(),
            profile.getAvatarUrl(),
            profile.getBio(),
            profile.getPhoneNumber(),
            profile.getDateOfBirth(),
            profile.getGender(),
            profile.getLocale(),
            profile.getTimezone(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}
