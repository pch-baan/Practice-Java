package com.practice.user.application.dto;

import com.practice.user.domain.enums.UserRoleEnum;
import com.practice.user.domain.enums.UserStatusEnum;
import com.practice.user.domain.model.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String username,
        String email,
        UserRoleEnum role,
        UserStatusEnum status,
        LocalDateTime createdAt
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUsername().getValue(),
                user.getEmail().getValue(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
