package com.practice.user.application.usecase;

import com.practice.user.application.dto.UserProfileResponseDto;
import com.practice.user.application.port.in.IGetUserProfileUseCase;
import com.practice.user.domain.exception.UserNotFoundException;
import com.practice.user.domain.port.out.IUserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetUserProfileUseCaseImpl implements IGetUserProfileUseCase {

    private final IUserProfileRepository userProfileRepository;

    @Override
    public UserProfileResponseDto findByUserId(UUID userId) {
        return userProfileRepository.findByUserId(userId)
            .map(UserProfileResponseDto::from)
            .orElseThrow(() -> new UserNotFoundException("User profile not found for userId: " + userId));
    }
}
