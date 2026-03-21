# Tối Ưu Workflow: POST /api/v1/users — Tạo User

## Bối Cảnh

Sau khi đánh giá toàn bộ workflow tạo user (điểm gốc: **8/10**), phát hiện 4 vấn đề
cần fix để nâng chất lượng code lên chuẩn production.

---

## Đánh Giá Gốc — Điểm Yếu Cần Fix

| Vấn đề | Hạng mục | Điểm trước | Nguy hiểm |
|--------|----------|------------|-----------|
| TOCTOU race condition | Application/Infrastructure | 7.5 | Cao — HTTP 500 thay vì 409 |
| Tạo Value Object 2 lần | Application | 7.5 | Thấp — lãng phí, gây nhầm |
| Domain enum dùng trực tiếp ở API | API layer | 7.5 | Thấp trong context này — xem ghi chú Fix 4 |
| Thiếu global exception handler | Error handling | 6.0 | Cao — mọi lỗi đều ra HTTP 500 |

---

## Fix 1 — Exception Hierarchy (Nền Tảng)

### Vấn đề

`UserDomainException` dùng cho mọi tình huống (conflict, not found, validation).
Không thể map đúng HTTP status code vì không phân biệt được loại lỗi.

### Quyết định thiết kế: Exception Hierarchy

```
UserDomainException          (base — 400 Bad Request, catch-all)
├── UserConflictException    (409 Conflict — email/username đã tồn tại)
└── UserNotFoundException    (404 Not Found — user/profile không tồn tại)
```

**Tại sao dùng hierarchy thay vì error code enum?**

- `@ExceptionHandler` trong Spring match theo **type** — handler tự động, không cần switch/case
- Thêm loại lỗi mới = thêm class mới, không sửa handler cũ (Open/Closed Principle)
- Pure Java, không cần Spring annotation — domain layer giữ sạch

### Files thay đổi

| File | Thay đổi |
|------|----------|
| `domain/exception/UserConflictException.java` | **Tạo mới** — extends UserDomainException |
| `domain/exception/UserNotFoundException.java` | **Tạo mới** — extends UserDomainException |
| `exception/GlobalExceptionHandler.java` | **Cập nhật** — thêm handler cho 2 exception mới, sửa catch-all UserDomainException từ 409 → 400 |
| `domain/service/UserDomainService.java` | **Sửa** — throw UserConflictException thay vì UserDomainException |
| `application/usecase/GetUserProfileUseCaseImpl.java` | **Sửa** — throw UserNotFoundException |
| `application/usecase/UpdateUserProfileUseCaseImpl.java` | **Sửa** — throw UserNotFoundException |

### Kết quả mapping HTTP

```
UserConflictException  → 409 Conflict
UserNotFoundException  → 404 Not Found
UserDomainException    → 400 Bad Request   (catch-all cho validation, business rule)
AuthDomainException    → 401 Unauthorized
MethodArgumentNotValid → 400 Bad Request   (Bean Validation errors)
```

---

## Fix 2 — TOCTOU Race Condition

### Vấn đề

```
Thread A: existsByEmail("bob@gmail.com") → false ✓
Thread B: existsByEmail("bob@gmail.com") → false ✓  ← chen vào đây
Thread A: userRepository.save()  → OK ✅
Thread B: userRepository.save()  → 💥 DataIntegrityViolationException (JPA)
                                           ↓ không ai catch
                                       HTTP 500 ❌
```

Giữa bước **check** và bước **write**, hai request đồng thời có thể vượt qua pre-check.
DB unique constraint bắt được — nhưng exception là `DataIntegrityViolationException` của
Spring/JPA, không phải `UserConflictException` của domain. Không có handler → HTTP 500.

### Giải pháp: Defense-in-depth

**Giữ pre-check** (fast-fail với message rõ ràng) + **thêm try/catch tại adapter** (safety-net).

```java
// UserPostgresqlAdapter.java
@Override
public User save(User user) {
    try {
        UserJpaEntity entity      = mapper.toJpaEntity(user);
        UserJpaEntity savedEntity = userJpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    } catch (DataIntegrityViolationException ex) {
        // Safety-net: concurrent request vượt qua pre-check → DB bắt được
        throw new UserConflictException("User already exists (concurrent registration detected)");
    }
}
```

### Tại sao catch ở adapter, không phải UseCase hay Controller?

```
api-portal / UserController           ← không nên biết JPA
user-service / CreateUserUseCaseImpl  ← không nên biết JPA
user-service / domain/...             ← không nên biết JPA
─────────────────────────────────────────────────────────
infrastructure / UserPostgresqlAdapter ← ĐÚNG CHỖ ✅
                  tầng duy nhất biết JPA tồn tại
```

`DataIntegrityViolationException` là **chi tiết kỹ thuật của JPA**. Nếu catch ở tầng trên,
domain/application layer bị ô nhiễm bởi Spring dependency — vi phạm Hexagonal Architecture.

### Luồng sau khi fix

```
Thread B: userJpaRepository.save()
    → DataIntegrityViolationException  (Spring/JPA — bị giữ lại ở infrastructure)
    → catch tại UserPostgresqlAdapter
    → throw UserConflictException      (domain exception — bay lên tự do)
    → GlobalExceptionHandler.handleUserConflict()
    → HTTP 409 Conflict ✅
```

### Trade-off

| | Không có try/catch | Có try/catch |
|--|---------------------|-------------|
| HTTP status | 500 ❌ | 409 ✅ |
| Error message | Stack trace / generic | "User already exists..." |
| Domain biết JPA? | Không (nếu catch đúng chỗ) | Không ✅ |
| Xảy ra khi nào | Race condition 2 request | Tương tự |

Pre-check (`existsByEmail`) vẫn giữ: **99% case** cho message cụ thể hơn
(`"Email already exists: bob@gmail.com"`). Try/catch là lưới an toàn cho **1% case concurrent**.

---

## Fix 3 — Redundant Value Object Creation

### Vấn đề

```java
// CreateUserUseCaseImpl.java — TRƯỚC KHI FIX
EmailVO email       = EmailVO.of(command.email());      // tạo VO lần 1 ✓
UsernameVO username = UsernameVO.of(command.username()); // tạo VO lần 1 ✓

userDomainService.validateUniqueConstraints(email, username);

// Bên trong User.create(String, String, String):
//   UsernameVO.of(username) ← tạo lại lần 2 ❌
//   EmailVO.of(email)       ← tạo lại lần 2 ❌
User user = User.create(command.username(), command.email(), passwordHash);
```

Validation chạy **2 lần** cho cùng 1 input trong cùng 1 flow.

### Giải pháp: đổi signature `User.create()` nhận VO

```java
// domain/model/User.java — SAU KHI FIX
public static User create(UsernameVO username, EmailVO email, String passwordHash) {
    return new User(
        UUID.randomUUID(),
        username,   // dùng thẳng, không tạo lại
        email,      // dùng thẳng
        PasswordHashVO.of(passwordHash),
        UserRoleEnum.USER,
        UserStatusEnum.ACTIVE,
        LocalDateTime.now(),
        LocalDateTime.now()
    );
}

// CreateUserUseCaseImpl.java — SAU KHI FIX
User user = User.create(username, email, passwordHash); // truyền VO trực tiếp ✅
```

### Lợi ích thêm: API tự document

```java
// TRƯỚC: String dễ nhầm thứ tự
User.create("bob",          "bob@gmail.com", hash)  ✓
User.create("bob@gmail.com", "bob",          hash)  ← nhầm thứ tự, compiler không bắt ❌

// SAU: VO có type riêng, compiler bắt lỗi ngay
User.create(username, email, hash)  ✓
User.create(email, username, hash)  ← compile error ✅ — không thể nhầm
```

---

## Fix 4 — API-level Enum cho Response

### Bối cảnh thực tế — Đây có phải "lỗi" không?

> **Câu trả lời thẳng: Không phải lỗi kỹ thuật, là good practice có điều kiện.**

`api-portal` đã phụ thuộc `user-service` theo dependency rule của project:
```
api-portal → user-service (application + domain)
```

Việc `UserResponse` dùng `UserRoleEnum` từ domain **không thêm dependency mới** —
dependency đó đã tồn tại. Không vi phạm Hexagonal Architecture.

**Khi nào dùng domain enum trực tiếp là ổn:**
- Internal API, cùng team, cùng repo (như project này)
- Domain enum thay đổi → compiler báo lỗi toàn bộ codebase ngay
- Không có external consumer độc lập (mobile app riêng, third-party)

**Khi nào CẦN tách ra API-level enum:**
- API public, có external consumers (mobile iOS/Android, third-party)
- Cần API versioning riêng biệt với domain evolution
- Domain team và API team là 2 team khác nhau

**Trong project này:** rủi ro gần bằng 0.
Fix này được giữ vì lợi ích về type safety và học pattern đúng chuẩn —
không phải vì code gốc sai.

### Thay đổi đã thực hiện

```java
// v1/user/response/UserRoleResponse.java   — MỚI
public enum UserRoleResponse { USER, ADMIN }

// v1/user/response/UserStatusResponse.java — MỚI
public enum UserStatusResponse { ACTIVE, INACTIVE }

// UserApiMapper.java — THÊM mapping
private UserRoleResponse toRoleResponse(UserRoleEnum role) {
    return switch (role) {
        case USER  -> UserRoleResponse.USER;
        case ADMIN -> UserRoleResponse.ADMIN;
    };
}
```

### Luồng với API-level enum

```
┌─── Domain World ────────────┐    ┌─── API World ──────────────┐
│  UserRoleEnum               │    │  UserRoleResponse          │
│  (đổi tự do nếu cần)        │───▶│  (contract với client)     │
└─────────────────────────────┘    └────────────────────────────┘
                              ↑
                    toRoleResponse() — lớp cách ly
                    (chỉ cần thiết khi có external consumer)
```

### Lợi ích thực tế dù không bắt buộc

| | Dùng domain enum trực tiếp | Dùng API-level enum |
|--|---------------------------|---------------------|
| Dependency vi phạm? | Không | Không |
| Compiler bắt thay đổi? | Có (trong monorepo) | Có |
| API contract độc lập? | Không | Có — cần cho external API |
| Boilerplate | Ít hơn | Thêm 2 enum + mapper |
| Swagger enum values | Có | Có |

---

## Tóm Tắt Toàn Bộ Thay Đổi

### Files mới tạo

```
user-service/domain/exception/
  ├── UserConflictException.java      409 Conflict
  └── UserNotFoundException.java      404 Not Found

api-portal/v1/user/response/
  ├── UserRoleResponse.java           API-level enum cho role
  └── UserStatusResponse.java         API-level enum cho status
```

### Files cập nhật

```
user-service/
  domain/model/User.java
    └── create(String,String,String) → create(UsernameVO,EmailVO,String)

  domain/service/UserDomainService.java
    └── throw UserDomainException → throw UserConflictException

  application/usecase/CreateUserUseCaseImpl.java
    └── User.create(command.username(), command.email(), hash)
        → User.create(username, email, hash)

  application/usecase/GetUserProfileUseCaseImpl.java
    └── throw UserDomainException → throw UserNotFoundException

  application/usecase/UpdateUserProfileUseCaseImpl.java
    └── throw UserDomainException → throw UserNotFoundException

  infrastructure/persistence/.../adapter/UserPostgresqlAdapter.java
    └── thêm try/catch DataIntegrityViolationException → UserConflictException

api-portal/
  exception/GlobalExceptionHandler.java
    └── thêm handler UserConflictException (409), UserNotFoundException (404)
    └── sửa UserDomainException catch-all: 409 → 400

  v1/user/response/UserResponse.java
    └── UserRoleEnum → UserRoleResponse
    └── UserStatusEnum → UserStatusResponse

  v1/user/mapper/UserApiMapper.java
    └── thêm toRoleResponse(), toStatusResponse()
```

### Thứ tự fix quan trọng

```
1. Exception Hierarchy  ← nền tảng, các fix khác cần
2. TOCTOU              ← cần UserConflictException từ bước 1
3. Redundant VO        ← độc lập, sửa domain model
4. Enum Leak           ← ít risk nhất, sửa cuối
```

---

## Q&A

### Q: `toRoleResponse()` có thực sự cần thiết không? Dùng thẳng domain enum được không?

**A:** Được — và trong project này (internal modular monolith) không vi phạm gì.

`api-portal` đã phụ thuộc `user-service` theo dependency rule. Dùng `UserRoleEnum` trong
`UserResponse` không thêm dependency mới. Đây **không phải lỗi**.

`toRoleResponse()` có giá trị khi API có **external consumers** (mobile, third-party):
```
Domain đổi nội bộ: USER → MEMBER
    ↓ không có mapper
API response: { "role": "MEMBER" }  ← thay đổi không kiểm soát
    ↓
Client iOS hardcode "USER" → crash ❌ — không được báo trước
```

Với external API: cần mapper để giữ contract ổn định độc lập với domain evolution.
Với internal API như project này: mapper là good practice, không phải bắt buộc.

---

### Q: Tại sao catch `DataIntegrityViolationException` ở adapter, không phải UseCase?

**A:** Vì `DataIntegrityViolationException` là exception của **Spring/JPA** — chi tiết kỹ thuật
của tầng infrastructure.

Nếu catch ở UseCase:
```java
// CreateUserUseCaseImpl.java
import org.springframework.dao.DataIntegrityViolationException; // ← Spring import trong domain ❌
```

UseCase (application layer) phải import Spring — vi phạm dependency rule của
Hexagonal Architecture. Domain/Application layer không được biết infrastructure tồn tại.

Adapter là tầng duy nhất biết JPA, biết Spring Data — nên catch ở đây, convert sang
domain exception (`UserConflictException`), rồi để domain exception bay lên tự do.

---

### Q: Pre-check `existsByEmail()` vẫn còn ý nghĩa không khi đã có try/catch?

**A:** Có — hai cơ chế phục vụ mục đích khác nhau:

| Cơ chế | Xử lý case nào | Error message |
|--------|---------------|---------------|
| Pre-check (`existsByEmail`) | 99% case bình thường | Rõ ràng: "Email already exists: bob@gmail.com" |
| Try/catch adapter | 1% case concurrent (race condition) | Generic: "User already exists (concurrent...)" |

Pre-check cho UX tốt (message cụ thể). Try/catch là lưới an toàn khi pre-check bị vượt qua.
Đây là pattern **defense-in-depth** — nhiều lớp bảo vệ, mỗi lớp một mục đích.

---

### Q: Tại sao đổi `User.create(String, String, String)` → `User.create(UsernameVO, EmailVO, String)`?

**A:** Ba lý do:

**1. Tránh validate 2 lần:**
```
UseCase tạo EmailVO    → validate format ← lần 1
User.create() tạo lại → validate format ← lần 2 (vô nghĩa, cùng input)
```

**2. Type safety — compiler bắt lỗi nhầm thứ tự:**
```java
// String dễ nhầm: compiler không bắt được
User.create("bob@gmail.com", "bob", hash) ← nhầm thứ tự ❌

// VO có type riêng: compiler báo lỗi ngay
User.create(emailVO, usernameVO, hash)    ← compile error ✅
```

**3. Signature tự document:**
`create(UsernameVO, EmailVO, String)` nói rõ: "caller phải validate trước (có VO)
rồi mới được tạo User" — không cần comment giải thích.
