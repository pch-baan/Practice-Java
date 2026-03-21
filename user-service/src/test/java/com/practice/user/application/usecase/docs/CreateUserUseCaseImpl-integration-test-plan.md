# Kế hoạch Integration Test — CreateUserUseCaseImpl

## Luồng code cần test

```
execute(CreateUserCommandDto)
    │
    ├─► EmailVO.of(email)           → throw UserDomainException nếu invalid
    ├─► UsernameVO.of(username)     → throw UserDomainException nếu invalid
    │
    ├─► userDomainService.validateUniqueConstraints(email, username)
    │       ├─► userRepository.existsByEmail(email)   → throw UserConflictException nếu trùng
    │       └─► userRepository.existsByUsername(user) → throw UserConflictException nếu trùng
    │
    ├─► passwordEncoder.encode(password)   → trả về hash
    ├─► User.create(username, email, hash) → domain object mới
    ├─► userRepository.save(user)          → lưu vào DB
    ├─► UserProfile.createEmpty(userId)    → profile rỗng với locale/timezone mặc định
    ├─► userProfileRepository.save(profile)→ lưu vào DB
    └─► UserResponseDto.from(savedUser)    → trả về response
```

---

## Chiến lược test

- **Loại test:** `@SpringBootTest` + DB thật (H2 in-memory hoặc Testcontainers PostgreSQL)
- **Transaction:** `@Transactional` trên từng test để rollback sau mỗi test case
- **Inject:** `@Autowired CreateUserUseCaseImpl` — dùng thật, không mock

---

## Danh sách test cases

### Nhóm 1 — Happy Path

| # | Tên test | Mục tiêu kiểm tra |
|---|---|---|
| 1 | `execute_withValidCommand_shouldReturnUserResponse` | Kết quả trả về có đúng username, email không |
| 2 | `execute_withValidCommand_shouldPersistUserToDatabase` | User thật được lưu vào DB |
| 3 | `execute_withValidCommand_shouldPersistUserProfileToDatabase` | UserProfile thật được lưu vào DB |

### Nhóm 2 — State mặc định sau khi tạo

| # | Tên test | Mục tiêu kiểm tra |
|---|---|---|
| 4 | `execute_success_userShouldHaveRoleUSER` | `user.role == USER` |
| 5 | `execute_success_userShouldHaveStatusACTIVE` | `user.status == ACTIVE` |
| 6 | `execute_success_passwordShouldBeEncodedNotPlainText` | password lưu DB là hash, khác plain text |
| 7 | `execute_success_profileShouldBelongToCreatedUser` | `profile.userId == user.id` |
| 8 | `execute_success_profileShouldHaveDefaultLocale` | `profile.locale == "vi-VN"` |
| 9 | `execute_success_profileShouldHaveDefaultTimezone` | `profile.timezone == "Asia/Ho_Chi_Minh"` |

### Nhóm 3 — Validation Email (EmailVO)

| # | Tên test | Input | Exception |
|---|---|---|---|
| 10 | `execute_withNullEmail_shouldThrow` | `email = null` | `UserDomainException` |
| 11 | `execute_withBlankEmail_shouldThrow` | `email = "  "` | `UserDomainException` |
| 12 | `execute_withInvalidEmailFormat_shouldThrow` | `email = "not-an-email"` | `UserDomainException` |

### Nhóm 4 — Validation Username (UsernameVO)

| # | Tên test | Input | Exception |
|---|---|---|---|
| 13 | `execute_withNullUsername_shouldThrow` | `username = null` | `UserDomainException` |
| 14 | `execute_withBlankUsername_shouldThrow` | `username = ""` | `UserDomainException` |
| 15 | `execute_withTooShortUsername_shouldThrow` | `username = "ab"` (< 3 ký tự) | `UserDomainException` |
| 16 | `execute_withTooLongUsername_shouldThrow` | `username = "a".repeat(51)` (> 50 ký tự) | `UserDomainException` |
| 17 | `execute_withSpecialCharsUsername_shouldThrow` | `username = "user@name!"` | `UserDomainException` |

### Nhóm 5 — Unique Constraint (UserDomainService)

| # | Tên test | Điều kiện trước | Exception |
|---|---|---|---|
| 18 | `execute_withDuplicateEmail_shouldThrow` | Đã có user cùng email trong DB | `UserConflictException` |
| 19 | `execute_withDuplicateUsername_shouldThrow` | Đã có user cùng username trong DB | `UserConflictException` |

### Nhóm 6 — Transaction

| # | Tên test | Mục tiêu kiểm tra |
|---|---|---|
| 20 | `execute_whenProfileSaveFails_shouldRollbackUser` | Nếu `userProfileRepository.save()` ném exception → user cũng không được lưu (rollback toàn bộ) |

---

## Skeleton code

```java
@SpringBootTest
@Transactional
class CreateUserUseCaseImplIntegrationTest {

    @Autowired
    private ICreateUserUseCase createUserUseCase;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IUserProfileRepository userProfileRepository;

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void execute_withValidCommand_shouldReturnUserResponse() {
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        assertThat(result.username()).isEqualTo("hieu123");
        assertThat(result.email()).isEqualTo("hieu@test.com");
    }

    @Test
    void execute_withValidCommand_shouldPersistUserToDatabase() {
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        createUserUseCase.execute(command);

        assertThat(userRepository.existsByEmail(EmailVO.of("hieu@test.com"))).isTrue();
    }

    @Test
    void execute_withValidCommand_shouldPersistUserProfileToDatabase() {
        var command = new CreateUserCommandDto("hieu123", "hieu@test.com", "Secret@123");

        UserResponseDto result = createUserUseCase.execute(command);

        // Kiểm tra profile tồn tại theo userId
        Optional<UserProfile> profile = userProfileRepository.findByUserId(UUID.fromString(result.id()));
        assertThat(profile).isPresent();
    }

    // ── State mặc định ────────────────────────────────────────────────────────

    @Test
    void execute_success_userShouldHaveRoleUSER() { ... }

    @Test
    void execute_success_userShouldHaveStatusACTIVE() { ... }

    @Test
    void execute_success_passwordShouldBeEncodedNotPlainText() { ... }

    @Test
    void execute_success_profileShouldHaveDefaultLocale() {
        // profile.locale == "vi-VN"
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void execute_withNullEmail_shouldThrowUserDomainException() {
        var command = new CreateUserCommandDto("hieu123", null, "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
            .isInstanceOf(UserDomainException.class);
    }

    @Test
    void execute_withTooShortUsername_shouldThrowUserDomainException() {
        var command = new CreateUserCommandDto("ab", "hieu@test.com", "Secret@123");

        assertThatThrownBy(() -> createUserUseCase.execute(command))
            .isInstanceOf(UserDomainException.class)
            .hasMessageContaining("between 3 and 50");
    }

    // ── Unique constraint ─────────────────────────────────────────────────────

    @Test
    void execute_withDuplicateEmail_shouldThrowUserConflictException() {
        // Setup: tạo user đầu tiên
        createUserUseCase.execute(new CreateUserCommandDto("user1", "dup@test.com", "Pass@123"));

        // Act: tạo user thứ hai cùng email
        assertThatThrownBy(() ->
            createUserUseCase.execute(new CreateUserCommandDto("user2", "dup@test.com", "Pass@123")))
            .isInstanceOf(UserConflictException.class)
            .hasMessageContaining("Email already exists");
    }

    // ── Transaction rollback ──────────────────────────────────────────────────

    @Test
    void execute_whenProfileSaveFails_shouldRollbackUser() {
        // Cần dùng @MockBean IUserProfileRepository để giả lập exception
        // → userRepository không được có record sau khi exception xảy ra
    }
}
```

---

## Lưu ý setup

- **Test #20 (rollback)** cần `@MockBean IUserProfileRepository` để force exception
  → Phải tách ra class riêng vì `@MockBean` không tương thích tốt với `@Transactional` test thông thường.
- **H2 vs Testcontainers:** H2 nhanh hơn cho CI, nhưng Testcontainers (PostgreSQL thật) sát với production hơn.
- **`@Transactional` trên test class** giúp mỗi test tự rollback → không cần cleanup thủ công.
