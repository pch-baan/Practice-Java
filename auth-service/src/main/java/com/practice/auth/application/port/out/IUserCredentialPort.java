package com.practice.auth.application.port.out;

import com.practice.auth.domain.model.UserCredential;

import java.util.Optional;
import java.util.UUID;

public interface IUserCredentialPort {

    Optional<UserCredential> findByUsername(String username);

    Optional<UserCredential> findByEmail(String email);

    Optional<UserCredential> findByUserId(UUID userId);
}
