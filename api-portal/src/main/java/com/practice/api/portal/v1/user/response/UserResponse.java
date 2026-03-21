package com.practice.api.portal.v1.user.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        UserRoleResponse role,
        UserStatusResponse status,
        LocalDateTime createdAt
) {}
