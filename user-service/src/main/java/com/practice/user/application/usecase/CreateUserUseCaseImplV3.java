package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.model.User;
import com.practice.user.domain.model.UserProfile;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import com.practice.user.domain.valueobject.UsernameVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V3 — DB Unique Constraint Strategy (loại bỏ pre-check SELECT).
 *
 * ── Vấn đề của V2 ────────────────────────────────────────────────────────────
 *
 * V2 vẫn gọi validateUniqueConstraints() trước khi INSERT:
 *
 *   [open connection] → existsByEmail (SELECT 1) → existsByUsername (SELECT 2)
 *                     → save(user) (INSERT 3) → save(profile) (INSERT 4)
 *   [close connection]
 *
 *   3 round-trips chỉ để insert 1 user → lãng phí dưới high load.
 *
 * ── Fix ──────────────────────────────────────────────────────────────────────
 *
 * Bỏ hoàn toàn pre-check. Dựa vào DB unique constraint (ràng buộc) làm nguồn sự thật:
 *
 *   [open connection] → save(user) (INSERT 1) → save(profile) (INSERT 2)
 *   [close connection]
 *
 *   Nếu email/username duplicate → DB unique constraint nổ → adapter bắt
 *   DataIntegrityViolationException và convert sang UserConflictException.
 *
 * ── Tại sao adapter vẫn dùng saveAndFlush()? ─────────────────────────────────
 *
 * saveAndFlush() flush ngay trong transaction → exception được bắt tại adapter,
 * convert sang UserConflictException (domain exception) trước khi bubble up.
 * Giữ clean boundary: tầng trên không bao giờ thấy DataIntegrityViolationException.
 *
 * ── So sánh các version ───────────────────────────────────────────────────────
 *
 *   V1: @Transactional bao BCrypt → connection held ~330ms → pool cạn > 160 req
 *   V2: BCrypt ngoài transaction → held ~30ms → pool cạn > 1670 req
 *       Nhưng vẫn có 2 SELECT thừa mỗi request.
 *   V3: Bỏ pre-check → 1 round-trip/user → tối ưu cho high load.
 *
 * Xem chi tiết:
 *   - docs/db-unique-constraint-strategy.md
 *   - docs/high-load-issues.md
 */
@Service
@RequiredArgsConstructor
public class CreateUserUseCaseImplV3 implements ICreateUserUseCase {

    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;

    @Override
    public UserResponseDto execute(CreateUserCommandDto command) {
        EmailVO email       = EmailVO.of(command.email());
        UsernameVO username = UsernameVO.of(command.username());

        // BCrypt chạy NGOÀI transaction — không chiếm DB connection trong lúc hash
        String passwordHash = passwordEncoder.encode(command.password());

        // Không pre-check existsByEmail / existsByUsername.
        // DB unique constraint là nguồn sự thật — adapter sẽ bắt và convert exception.
        return new TransactionTemplate(transactionManager).execute(status -> {
            User savedUser = userRepository.save(User.create(username, email, passwordHash));

            userProfileRepository.save(UserProfile.createEmpty(savedUser.getId()));

            return UserResponseDto.from(savedUser);
        });
    }
}
