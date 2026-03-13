package com.practice.api.portal.user.mapper;

import com.practice.api.portal.user.request.CreateUserRequest;
import com.practice.api.portal.user.response.UserResponse;
import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
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
                dto.role(),
                dto.status(),
                dto.createdAt()
        );
    }
}
