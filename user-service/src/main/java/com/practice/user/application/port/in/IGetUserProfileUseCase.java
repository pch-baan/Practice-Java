package com.practice.user.application.port.in;

import com.practice.user.application.dto.UserProfileResponseDto;

import java.util.UUID;

public interface IGetUserProfileUseCase {

    UserProfileResponseDto findByUserId(UUID userId);
}
