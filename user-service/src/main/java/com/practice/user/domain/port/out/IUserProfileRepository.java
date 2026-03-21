package com.practice.user.domain.port.out;

import com.practice.user.domain.model.UserProfile;

import java.util.Optional;
import java.util.UUID;

public interface IUserProfileRepository {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findByUserId(UUID userId);
}
