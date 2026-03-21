package com.practice.api.portal.v1.user.mapper;

import com.practice.api.portal.v1.user.request.CreateUserRequest;
import com.practice.api.portal.v1.user.response.UserResponse;
import com.practice.api.portal.v1.user.response.UserRoleResponse;
import com.practice.api.portal.v1.user.response.UserStatusResponse;
import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.domain.enums.UserRoleEnum;
import com.practice.user.domain.enums.UserStatusEnum;
import org.springframework.stereotype.Component;

@Component
public class UserApiMapper {

    public CreateUserCommandDto toCommand(CreateUserRequest request) {
        return new CreateUserCommandDto(
                request.username(),
                request.email(),
                request.password()
        );
    }

    public UserResponse toResponse(UserResponseDto dto) {
        return new UserResponse(
                dto.id(),
                dto.username(),
                dto.email(),
                toRoleResponse(dto.role()),
                toStatusResponse(dto.status()),
                dto.createdAt()
        );
    }

    private UserRoleResponse toRoleResponse(UserRoleEnum role) {
        return switch (role) {
            case USER  -> UserRoleResponse.USER;
            case ADMIN -> UserRoleResponse.ADMIN;
        };
    }

    private UserStatusResponse toStatusResponse(UserStatusEnum status) {
        return switch (status) {
            case ACTIVE   -> UserStatusResponse.ACTIVE;
            case INACTIVE -> UserStatusResponse.INACTIVE;
        };
    }
}
