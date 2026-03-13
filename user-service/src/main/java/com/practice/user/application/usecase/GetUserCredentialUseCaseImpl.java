package com.practice.user.application.usecase;

import com.practice.user.application.dto.UserCredentialDto;
import com.practice.user.application.port.in.IGetUserCredentialUseCase;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetUserCredentialUseCaseImpl implements IGetUserCredentialUseCase {

    private final IUserRepository userRepository;

    @Override
    public Optional<UserCredentialDto> findByUsername(String username) {
        return userRepository.findByUsername(UsernameVO.of(username))
            .map(user -> new UserCredentialDto(
                user.getId(),
                user.getUsername().getValue(),
                user.getPasswordHash().getValue(),
                user.getRole().name(),
                user.getStatus().name()
            ));
    }

    @Override
    public Optional<UserCredentialDto> findByEmail(String email) {
        return userRepository.findByEmail(EmailVO.of(email))
            .map(user -> new UserCredentialDto(
                user.getId(),
                user.getUsername().getValue(),
                user.getPasswordHash().getValue(),
                user.getRole().name(),
                user.getStatus().name()
            ));
    }

    @Override
    public Optional<UserCredentialDto> findByUserId(UUID userId) {
        return userRepository.findById(userId)
            .map(user -> new UserCredentialDto(
                user.getId(),
                user.getUsername().getValue(),
                user.getPasswordHash().getValue(),
                user.getRole().name(),
                user.getStatus().name()
            ));
    }
}
