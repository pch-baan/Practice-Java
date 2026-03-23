package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.domain.model.User;
import com.practice.user.domain.model.UserProfile;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.domain.service.UserDomainService;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [REFERENCE ONLY — KHÔNG DÙNG TRONG PRODUCTION]
 *
 * Giữ lại để đối chiếu với CreateUserUseCaseImplV2.
 *
 * Vấn đề: passwordEncoder.encode() nằm TRONG @Transactional
 * → DB connection bị giữ trong suốt thời gian BCrypt hash (~300ms)
 * → Dưới high load, connection pool cạn kiệt (pool=10, timeout=5s → lỗi khi > 160 req đồng thời)
 *
 * Xem chi tiết:
 *   - docs/high-load-issues.md
 *   - docs/connection-pool-exhaustion-analysis.md
 *   - CreateUserConnectionPoolExhaustionTest.java
 */
// @Service  ← đã tắt, bean không được đăng ký vào Spring context
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements ICreateUserUseCase {

    private final UserDomainService userDomainService;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponseDto execute(CreateUserCommandDto command) {
        EmailVO email = EmailVO.of(command.email());
        UsernameVO username = UsernameVO.of(command.username());

        userDomainService.validateUniqueConstraints(email, username);

        String passwordHash = passwordEncoder.encode(command.password());

        User user = User.create(username, email, passwordHash);

        User savedUser = userRepository.save(user);

        userProfileRepository.save(UserProfile.createEmpty(savedUser.getId()));

        return UserResponseDto.from(savedUser);
    }
}
