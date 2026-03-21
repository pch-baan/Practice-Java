package com.practice.user.application.port.in;

import com.practice.user.application.dto.UpdateUserProfileCommandDto;
import com.practice.user.application.dto.UserProfileResponseDto;

import java.util.UUID;

public interface IUpdateUserProfileUseCase {

    UserProfileResponseDto execute(UUID userId, UpdateUserProfileCommandDto command);
}
