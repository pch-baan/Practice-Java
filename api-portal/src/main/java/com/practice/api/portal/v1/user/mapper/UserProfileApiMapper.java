package com.practice.api.portal.v1.user.mapper;

import com.practice.api.portal.v1.user.request.UpdateUserProfileRequest;
import com.practice.api.portal.v1.user.response.UserProfileResponse;
import com.practice.user.application.dto.UpdateUserProfileCommandDto;
import com.practice.user.application.dto.UserProfileResponseDto;
import com.practice.user.domain.enums.GenderEnum;
import org.springframework.stereotype.Component;

@Component
public class UserProfileApiMapper {

    public UpdateUserProfileCommandDto toCommand(UpdateUserProfileRequest request) {
        GenderEnum gender = request.gender() != null ? GenderEnum.valueOf(request.gender()) : null;
        return new UpdateUserProfileCommandDto(
            request.fullName(),
            request.displayName(),
            request.avatarUrl(),
            request.bio(),
            request.phoneNumber(),
            request.dateOfBirth(),
            gender,
            request.locale(),
            request.timezone()
        );
    }

    public UserProfileResponse toResponse(UserProfileResponseDto dto) {
        return new UserProfileResponse(
            dto.id(),
            dto.userId(),
            dto.fullName(),
            dto.displayName(),
            dto.avatarUrl(),
            dto.bio(),
            dto.phoneNumber(),
            dto.dateOfBirth(),
            dto.gender() != null ? dto.gender().name() : null,
            dto.locale(),
            dto.timezone(),
            dto.createdAt(),
            dto.updatedAt()
        );
    }
}
