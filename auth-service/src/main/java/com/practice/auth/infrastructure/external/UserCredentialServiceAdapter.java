package com.practice.auth.infrastructure.external;

import com.practice.auth.domain.model.UserCredential;
import com.practice.auth.application.port.out.IUserCredentialPort;
import com.practice.user.application.port.in.IGetUserCredentialUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class UserCredentialServiceAdapter implements IUserCredentialPort {

    private final IGetUserCredentialUseCase getUserCredentialUseCase;

    @Override
    public Optional<UserCredential> findByUsername(String username) {
        return getUserCredentialUseCase.findByUsername(username)
            .map(dto -> new UserCredential(
                dto.userId(),
                dto.username(),
                dto.passwordHash(),
                dto.role(),
                dto.status()
            ));
    }

    @Override
    public Optional<UserCredential> findByEmail(String email) {
        return getUserCredentialUseCase.findByEmail(email)
            .map(dto -> new UserCredential(
                dto.userId(),
                dto.username(),
                dto.passwordHash(),
                dto.role(),
                dto.status()
            ));
    }

    @Override
    public Optional<UserCredential> findByUserId(UUID userId) {
        return getUserCredentialUseCase.findByUserId(userId)
            .map(dto -> new UserCredential(
                dto.userId(),
                dto.username(),
                dto.passwordHash(),
                dto.role(),
                dto.status()
            ));
    }
}
