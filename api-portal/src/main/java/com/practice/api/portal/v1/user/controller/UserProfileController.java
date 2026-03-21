package com.practice.api.portal.v1.user.controller;

import com.practice.api.portal.v1.user.mapper.UserProfileApiMapper;
import com.practice.api.portal.v1.user.request.UpdateUserProfileRequest;
import com.practice.api.portal.v1.user.response.UserProfileResponse;
import com.practice.user.application.port.in.IGetUserProfileUseCase;
import com.practice.user.application.port.in.IUpdateUserProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final IGetUserProfileUseCase getProfileUseCase;
    private final IUpdateUserProfileUseCase updateProfileUseCase;
    private final UserProfileApiMapper userProfileApiMapper;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID userId) {
        var dto = getProfileUseCase.findByUserId(userId);
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
        @PathVariable UUID userId,
        @RequestBody UpdateUserProfileRequest request
    ) {
        var command = userProfileApiMapper.toCommand(request);
        var dto     = updateProfileUseCase.execute(userId, command);
        return ResponseEntity.ok(userProfileApiMapper.toResponse(dto));
    }
}
