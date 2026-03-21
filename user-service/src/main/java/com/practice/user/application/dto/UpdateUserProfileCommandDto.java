package com.practice.user.application.dto;

import com.practice.user.domain.enums.GenderEnum;

import java.time.LocalDate;

public record UpdateUserProfileCommandDto(
    String fullName,
    String displayName,
    String avatarUrl,
    String bio,
    String phoneNumber,
    LocalDate dateOfBirth,
    GenderEnum gender,
    String locale,
    String timezone
) {}
