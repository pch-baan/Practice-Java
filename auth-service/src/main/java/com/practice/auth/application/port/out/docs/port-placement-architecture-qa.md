# Q&A: Port Placement — Domain vs Application Layer

> Ngày: 2026-03-23
> Context: auth-service, modular monolith với DDD layering

---

## Q1: `ICreateUserPort` nằm ở `domain/port/out` có đúng kiến trúc không?

**Câu hỏi gốc:**

```java
// ① tạo user qua user-service — status = PENDING
CreatedUserResult created = createUserPort.createUser(
        command.username(), command.email(), command.password());
```

`ICreateUserPort` đang nằm ở `com.practice.auth.domain.port.out` — có đúng kiến trúc không?

**Trả lời:**

Theo `CLAUDE.md` của project:

```
application/  ← Port interfaces (in/ and out/)
domain/       ← Pure Java. Entities, Value Objects, Domain Services.
```

`ICreateUserPort` **sai chỗ** vì:
- Việc gọi sang `user-service` để tạo user là **orchestration logic** (application layer)
- Domain layer không nên biết việc user được tạo ở service khác
- Không nhất quán với `IEmailPort` cùng loại (output port dùng cho infrastructure) đang nằm đúng ở `application/port/out`

**Lưu ý:** Dependency direction không bị vi phạm (`application` → `domain` là cho phép), nhưng **phân loại port sai layer**.

---

## Q2: Phân tích toàn bộ user-service và auth-service — có thật sự cần đổi không?

**Toàn cảnh port placement trước khi đổi:**

### auth-service — `domain/port/out` (trước khi đổi)

| Port | Loại | Đánh giá |
|---|---|---|
| `IRefreshTokenRepository` | Repository của domain entity `RefreshToken` | ✅ Đúng chỗ |
| `IEmailVerificationTokenRepository` | Repository của domain entity `EmailVerificationToken` | ✅ Đúng chỗ |
| `IUserCredentialPort` | Cross-service call → gọi `user-service` | ⚠️ Sai chỗ |
| `ICreateUserPort` | Cross-service call → gọi `user-service` | ⚠️ Sai chỗ |
| `IActivateUserPort` | Cross-service call → gọi `user-service` | ⚠️ Sai chỗ |

### auth-service — `application/port/out` (trước khi đổi)

| Port | Loại | Đánh giá |
|---|---|---|
| `IJwtPort` | Infrastructure: JWT | ✅ Đúng chỗ |
| `IEmailPort` | Infrastructure: email | ✅ Đúng chỗ |

### user-service — `domain/port/out`

| Port | Loại | Đánh giá |
|---|---|---|
| `IUserRepository` | Repository của domain entity `User` | ✅ Đúng chỗ |
| `IUserProfileRepository` | Repository của domain entity `UserProfile` | ✅ Đúng chỗ |

**Nguyên tắc phân loại:**

```
domain/port/out   ← Repository interfaces cho domain entities
                     (domain sở hữu aggregate → domain định nghĩa repository của nó)

application/port/out ← Cross-service calls + Infrastructure ports
                       (email, JWT, gọi sang service khác)
```

**Kết luận:** User-service không cần đổi. Auth-service cần di chuyển 3 cross-service ports.

---

## Q3: Thực hiện thay đổi

**Các file đã di chuyển** từ `domain/port/out` → `application/port/out`:

- `ICreateUserPort.java`
- `IActivateUserPort.java`
- `IUserCredentialPort.java`

**Các file đã cập nhật import:**

| File | Layer |
|---|---|
| `RegisterUseCaseImpl.java` | application/usecase |
| `LoginUseCaseImpl.java` | application/usecase |
| `VerifyEmailUseCaseImpl.java` | application/usecase |
| `RefreshTokenUseCaseImpl.java` | application/usecase |
| `CreateUserServiceAdapter.java` | infrastructure/external |
| `ActivateUserServiceAdapter.java` | infrastructure/external |
| `UserCredentialServiceAdapter.java` | infrastructure/external |

**Kết quả sau khi đổi — `domain/port/out` chỉ còn:**

```
domain/port/out/
├── IRefreshTokenRepository.java           ← repository của RefreshToken
└── IEmailVerificationTokenRepository.java ← repository của EmailVerificationToken
```

**`application/port/out` sau khi đổi:**

```
application/port/out/
├── IActivateUserPort.java    ← cross-service: activate user
├── ICreateUserPort.java      ← cross-service: create user
├── IEmailPort.java           ← infrastructure: email
├── IJwtPort.java             ← infrastructure: JWT
└── IUserCredentialPort.java  ← cross-service: fetch user credentials
```

---

## Tóm tắt quy tắc

```
domain/port/out   = "Domain cần lưu/lấy entity của chính nó"
                    → Repository interfaces (IUserRepository, IRefreshTokenRepository...)

application/port/out = "Use case cần gọi ra ngoài để orchestrate"
                       → Cross-service ports (ICreateUserPort, IActivateUserPort...)
                       → Infrastructure ports (IJwtPort, IEmailPort...)
```
