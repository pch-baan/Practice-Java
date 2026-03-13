package com.practice.api.portal.user.response;

import com.practice.user.domain.enums.UserRoleEnum;
import com.practice.user.domain.enums.UserStatusEnum;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        UserRoleEnum role,
        UserStatusEnum status,
        LocalDateTime createdAt
) {}
