package com.practice.user.application.usecase;

import com.practice.user.application.dto.UpdateUserProfileCommandDto;
import com.practice.user.application.dto.UserProfileResponseDto;
import com.practice.user.application.port.in.IUpdateUserProfileUseCase;
import com.practice.user.domain.exception.UserNotFoundException;
import com.practice.user.domain.model.UserProfile;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.domain.port.out.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileUseCaseImpl implements IUpdateUserProfileUseCase {

    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public UserProfileResponseDto execute(UUID userId, UpdateUserProfileCommandDto command) {
        // Guard: ensure user exists before creating/updating profile
        userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Upsert: load existing profile or create a new empty one
        UserProfile profile = userProfileRepository.findByUserId(userId)
            .orElseGet(() -> UserProfile.createEmpty(userId));

        profile.update(
            command.fullName(),
            command.displayName(),
            command.avatarUrl(),
            command.bio(),
            command.phoneNumber(),
            command.dateOfBirth(),
            command.gender(),
            command.locale(),
            command.timezone()
        );

        return UserProfileResponseDto.from(userProfileRepository.save(profile));
    }
}
