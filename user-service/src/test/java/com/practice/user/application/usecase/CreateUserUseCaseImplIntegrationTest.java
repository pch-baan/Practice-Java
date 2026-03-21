package com.practice.user.application.usecase;

import com.practice.user.application.dto.CreateUserCommandDto;
import com.practice.user.application.dto.UserResponseDto;
import com.practice.user.application.port.in.ICreateUserUseCase;
import com.practice.user.domain.enums.UserRoleEnum;
import com.practice.user.domain.enums.UserStatusEnum;
import com.practice.user.domain.exception.UserConflictException;
import com.practice.user.domain.exception.UserDomainException;
import com.practice.user.domain.port.out.IUserProfileRepository;
import com.practice.user.domain.port.out.IUserRepository;
import com.practice.user.domain.valueobject.EmailVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests cho CreateUserUseCaseImpl.
 *
 * Mỗi test chạy trong 1 transaction riêng → tự động rollback sau khi kết thúc.
 * Không cần cleanup thủ công.
 *
 * Yêu cầu: PostgreSQL đang chạy (docker-compose up postgres -d).
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CreateUserUseCaseImplIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CreateUserUseCaseImplIntegrationTest.class);

    @Autowired private ICreateUserUseCase createUserUseCase;
    @Autowired private IUserRepository userRepository;
    @Autowired private IUserProfileRepository userProfileRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────────────────
    // Nhóm 1 — Happy Path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Happy Path] Tạo user hợp lệ → response trả về đúng id, username, email")
    void execute_withValidCommand_shouldReturnCorrectResponse() {
        log.info("▶ [HAPPY PATH] Tạo user hợp lệ → kiểm tra response trả về");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        log.info("  ✔ Response: id={}, username={}, email={}, role={}, status={}",
                result.id(), result.username(), result.email(), result.role(), result.status());

        assertThat(result.id()).isNotNull();
        assertThat(result.username()).isEqualTo("hieu123");
        assertThat(result.email()).isEqualTo("hieu@test.com");
    }

    @Test
    @DisplayName("[Happy Path] Tạo user hợp lệ → user được lưu vào DB")
    void execute_withValidCommand_shouldPersistUserToDatabase() {
        log.info("▶ [HAPPY PATH] Tạo user → kiểm tra user tồn tại trong DB");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        createUserUseCase.execute(command);

        boolean exists = userRepository.existsByEmail(EmailVO.of("hieu@test.com"));
        log.info("  ✔ existsByEmail('hieu@test.com') = {}", exists);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("[Happy Path] Tạo user hợp lệ → UserProfile được lưu vào DB")
    void execute_withValidCommand_shouldPersistUserProfileToDatabase() {
        log.info("▶ [HAPPY PATH] Tạo user → kiểm tra UserProfile được lưu vào DB");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        var profile = userProfileRepository.findByUserId(result.id());
        log.info("  ✔ UserProfile tìm được: present={}, value={}",
                profile.isPresent(), profile.map(Object::toString).orElse("NOT FOUND"));

        assertThat(profile).isPresent();
        assertThat(profile.get().getUserId()).isEqualTo(result.id());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nhóm 2 — State mặc định sau khi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[State] User mới tạo phải có role = USER")
    void execute_success_userShouldHaveRoleUSER() {
        log.info("▶ [STATE] Kiểm tra user mới tạo có role = USER");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        log.info("  ✔ role = {}", result.role());
        assertThat(result.role()).isEqualTo(UserRoleEnum.USER);
    }

    @Test
    @DisplayName("[State] User mới tạo phải có status = ACTIVE")
    void execute_success_userShouldHaveStatusACTIVE() {
        log.info("▶ [STATE] Kiểm tra user mới tạo có status = ACTIVE");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        log.info("  ✔ status = {}", result.status());
        assertThat(result.status()).isEqualTo(UserStatusEnum.ACTIVE);
    }

    @Test
    @DisplayName("[State] Password phải được encode (BCrypt), không lưu plain text vào DB")
    void execute_success_passwordShouldBeEncodedNotPlainText() {
        log.info("▶ [STATE] Kiểm tra password được encode (BCrypt), không lưu plain text");
        String plainPassword = "Secret@123";
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", plainPassword);

        createUserUseCase.execute(command);

        var savedUser = userRepository.findByEmail(EmailVO.of("hieu@test.com")).orElseThrow();
        String storedHash = savedUser.getPasswordHash().getValue();

        log.info("  ✔ plainPassword   = {}", plainPassword);
        log.info("  ✔ storedHash      = {}", storedHash);
        log.info("  ✔ matches(plain, hash) = {}", passwordEncoder.matches(plainPassword, storedHash));

        assertThat(storedHash).isNotEqualTo(plainPassword);
        assertThat(passwordEncoder.matches(plainPassword, storedHash)).isTrue();
    }

    @Test
    @DisplayName("[State] UserProfile.userId phải trỏ đúng vào User vừa tạo")
    void execute_success_profileShouldBelongToCreatedUser() {
        log.info("▶ [STATE] Kiểm tra UserProfile.userId == User.id");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        var profile = userProfileRepository.findByUserId(result.id()).orElseThrow();

        log.info("  ✔ user.id         = {}", result.id());
        log.info("  ✔ profile.userId  = {}", profile.getUserId());

        assertThat(profile.getUserId()).isEqualTo(result.id());
    }

    @Test
    @DisplayName("[State] UserProfile mới tạo phải có locale mặc định = 'vi-VN'")
    void execute_success_profileShouldHaveDefaultLocale() {
        log.info("▶ [STATE] Kiểm tra UserProfile.locale = 'vi-VN' (mặc định)");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        var profile = userProfileRepository.findByUserId(result.id()).orElseThrow();

        log.info("  ✔ profile.locale = {}", profile.getLocale());
        assertThat(profile.getLocale()).isEqualTo("vi-VN");
    }

    @Test
    @DisplayName("[State] UserProfile mới tạo phải có timezone mặc định = 'Asia/Ho_Chi_Minh'")
    void execute_success_profileShouldHaveDefaultTimezone() {
        log.info("▶ [STATE] Kiểm tra UserProfile.timezone = 'Asia/Ho_Chi_Minh' (mặc định)");
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        var profile = userProfileRepository.findByUserId(result.id()).orElseThrow();

        log.info("  ✔ profile.timezone = {}", profile.getTimezone());
        assertThat(profile.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nhóm 3 — Validation Email (EmailVO)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Validation - Email] email = null → throw UserDomainException")
    void execute_withNullEmail_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] email = null → expect UserDomainException");
        var command = new CreateUserCommandDto("hieu123", null, "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Validation - Email] email = blank → throw UserDomainException")
    void execute_withBlankEmail_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] email = '  ' (blank) → expect UserDomainException");
        var command = new CreateUserCommandDto("hieu123", "  ", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Validation - Email] email sai format ('not-an-email') → throw UserDomainException")
    void execute_withInvalidEmailFormat_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] email = 'not-an-email' (format sai) → expect UserDomainException");
        var command = new CreateUserCommandDto("hieu123", "not-an-email", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nhóm 4 — Validation Username (UsernameVO)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Validation - Username] username = null → throw UserDomainException")
    void execute_withNullUsername_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] username = null → expect UserDomainException");
        var command = new CreateUserCommandDto(null, "hieu@test.com", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Validation - Username] username = blank → throw UserDomainException")
    void execute_withBlankUsername_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] username = '' (blank) → expect UserDomainException");
        var command = new CreateUserCommandDto("", "hieu@test.com", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Validation - Username] username quá ngắn (length < 3) → throw UserDomainException")
    void execute_withTooShortUsername_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] username = 'ab' (length=2, min=3) → expect UserDomainException");
        var command = new CreateUserCommandDto("ab", "hieu@test.com", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .hasMessageContaining("between 3 and 50")
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Validation - Username] username quá dài (length > 50) → throw UserDomainException")
    void execute_withTooLongUsername_shouldThrowUserDomainException() {
        String longUsername = "a".repeat(51);
        log.info("▶ [VALIDATION] username length={} (max=50) → expect UserDomainException", longUsername.length());
        var command = new CreateUserCommandDto(longUsername, "hieu@test.com", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .hasMessageContaining("between 3 and 50")
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Validation - Username] username chứa ký tự đặc biệt không hợp lệ → throw UserDomainException")
    void execute_withSpecialCharsUsername_shouldThrowUserDomainException() {
        log.info("▶ [VALIDATION] username = 'user@name!' (ký tự đặc biệt không hợp lệ) → expect UserDomainException");
        var command = new CreateUserCommandDto("user@name!", "hieu@test.com", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
                .isInstanceOf(UserDomainException.class)
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nhóm 5 — Unique Constraint (UserDomainService)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Unique] Tạo 2 user cùng email → throw UserConflictException")
    void execute_withDuplicateEmail_shouldThrowUserConflictException() {
        log.info("▶ [UNIQUE] Tạo 2 user cùng email → expect UserConflictException");

        createUserUseCase.execute(new CreateUserCommandDto("user1", "dup@test.com", "Secret@123"));
        log.info("  → User 1 (username=user1, email=dup@test.com) tạo thành công");

        assertThatThrownBy(() ->
                createUserUseCase.execute(new CreateUserCommandDto("user2", "dup@test.com", "Secret@123")))
                .isInstanceOf(UserConflictException.class)
                .hasMessageContaining("Email already exists")
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }

    @Test
    @DisplayName("[Unique] Tạo 2 user cùng username → throw UserConflictException")
    void execute_withDuplicateUsername_shouldThrowUserConflictException() {
        log.info("▶ [UNIQUE] Tạo 2 user cùng username → expect UserConflictException");

        createUserUseCase.execute(new CreateUserCommandDto("dupuser", "user1@test.com", "Secret@123"));
        log.info("  → User 1 (username=dupuser, email=user1@test.com) tạo thành công");

        assertThatThrownBy(() ->
                createUserUseCase.execute(new CreateUserCommandDto("dupuser", "user2@test.com", "Secret@123")))
                .isInstanceOf(UserConflictException.class)
                .hasMessageContaining("Username already exists")
                .satisfies(ex -> log.info("  ✔ Exception caught: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage()));
    }
}
