package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.domain.model.User;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.service.UserDomainService;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements ICreateUserUseCase {

    private final UserDomainService userDomainService;
    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponseDto execute(CreateUserCommandDto command) {
        EmailVO email = EmailVO.of(command.email());
        UsernameVO username = UsernameVO.of(command.username());

        userDomainService.validateUniqueConstraints(email, username);

        String passwordHash = passwordEncoder.encode(command.password());

        User user = User.create(command.username(), command.email(), passwordHash);

        User savedUser = userRepository.save(user);

        return UserResponseDto.from(savedUser);
    }
}
