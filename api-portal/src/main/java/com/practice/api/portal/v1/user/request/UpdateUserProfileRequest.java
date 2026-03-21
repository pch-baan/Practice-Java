package com.practice.api.portal.v1.user.request;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
    String fullName,
    String displayName,
    String avatarUrl,
    String bio,
    String phoneNumber,
    LocalDate dateOfBirth,
    String gender,
    String locale,
    String timezone
) {}
