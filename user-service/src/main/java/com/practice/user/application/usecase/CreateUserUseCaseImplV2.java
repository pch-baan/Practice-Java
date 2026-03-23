package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.model.User;
import com.practice.user.domain.model.UserProfile;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.service.UserDomainService;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fix cho vấn đề connection pool exhaustion trong CreateUserUseCaseImpl.
 *
 * ── Vấn đề (CreateUserUseCaseImpl — bản cũ) ─────────────────────────────────
 *
 * @Transactional bao toàn bộ execute() → DB connection bị giữ trong khi BCrypt hash:
 *
 *   [open connection] → existsByEmail → existsByUsername
 *                     → BCrypt 300ms (connection bị giữ, không làm gì)
 *                     → save(user) → save(profile)
 *   [close connection]
 *
 *   Hold time ≈ 330ms → pool=10, timeout=5s → lỗi khi > 160 request đồng thời.
 *
 * ── Fix ──────────────────────────────────────────────────────────────────────
 *
 * BCrypt chạy TRƯỚC khi mở transaction. Connection chỉ được lấy khi thật sự cần DB:
 *
 *   BCrypt 300ms (không cần connection)
 *   [open connection] → existsByEmail → existsByUsername → save(user) → save(profile)
 *   [close connection]
 *
 *   Hold time ≈ 30ms → lỗi khi > 1,670 request đồng thời (10x tốt hơn).
 *
 * ── Tại sao dùng TransactionTemplate thay vì @Transactional? ─────────────────
 *
 * Spring AOP dùng proxy để intercept @Transactional. Khi 1 method trong cùng 1 bean
 * gọi method khác có @Transactional trong bean đó (self-invocation), Spring proxy
 * KHÔNG intercept được → @Transactional bị IGNORED.
 *
 * Ví dụ KHÔNG hoạt động:
 *   public UserResponseDto execute(...) {
 *       encoder.encode(...);    // ngoài transaction
 *       return this.persist();  // @Transactional ở đây bị ignored ✘
 *   }
 *   @Transactional
 *   private UserResponseDto persist(...) { ... }
 *
 * TransactionTemplate mở transaction theo cách programmatic — không qua proxy,
 * không bị vấn đề self-invocation → luôn hoạt động đúng.
 */
// @Service  ← đã tắt, bean không được đăng ký vào Spring context
@RequiredArgsConstructor
public class CreateUserUseCaseImplV2 implements ICreateUserUseCase {

    private final UserDomainService userDomainService;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;

    @Override
    public UserResponseDto execute(CreateUserCommandDto command) {
        EmailVO email    = EmailVO.of(command.email());
        UsernameVO username = UsernameVO.of(command.username());

        // BCrypt chạy NGOÀI transaction — không chiếm DB connection trong lúc hash
        String passwordHash = passwordEncoder.encode(command.password());

        // Mở transaction CHỈ khi thật sự cần thao tác với DB
        return new TransactionTemplate(transactionManager).execute(status -> {
            userDomainService.validateUniqueConstraints(email, username);

            User savedUser = userRepository.save(User.create(username, email, passwordHash));

            userProfileRepository.save(UserProfile.createEmpty(savedUser.getId()));

            return UserResponseDto.from(savedUser);
        });
    }
}
