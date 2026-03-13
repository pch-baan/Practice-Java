package com.practice.user.application.port.in;

import com.practice.user.application.dto.UserCredentialDto;

import java.util.Optional;
import java.util.UUID;

public interface IGetUserCredentialUseCase {

    Optional<UserCredentialDto> findByUsername(String username);

    Optional<UserCredentialDto> findByEmail(String email);

    Optional<UserCredentialDto> findByUserId(UUID userId);
}
