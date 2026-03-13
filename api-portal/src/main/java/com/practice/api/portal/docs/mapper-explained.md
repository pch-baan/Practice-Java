# Mapper là gì và tại sao cần nó?

## Câu chuyện: Khách sạn và bếp nhà hàng

Tưởng tượng bạn đến nhà hàng và gọi món:

```
Khách (Client)
    │
    │  Phiếu gọi món (CreateUserRequest)
    │  "Tôi muốn: username=john, email=..., password=..."
    ▼
Bồi bàn (Controller)
    │
    │  ??? Phiếu gọi món KHÔNG vào thẳng bếp được
    │      vì bếp có ngôn ngữ riêng!
    ▼
Người dịch (UserApiMapper)
    │
    │  Phiếu nội bộ (CreateUserCommandDto)
    │  "Chef: cần username=john, email=..., password=..."
    ▼
Bếp trưởng (UseCase)
```

---

## Tại sao KHÔNG dùng thẳng `CreateUserRequest` vào UseCase?

Nhìn vào 2 class:

```java
// API Layer — CreateUserRequest
public record CreateUserRequest(
    @NotBlank           // ← annotation HTTP/Validation
    @Size(min=3, max=50)// ← annotation HTTP/Validation
    String username,
    @Email              // ← annotation HTTP/Validation
    String email,
    ...
)

// Application Layer — CreateUserCommandDto
public record CreateUserCommandDto(
    String username,    // ← sạch, không biết gì về HTTP
    String email,
    String password
)
```

**Vấn đề nếu không có Mapper:**

```
UserController ──uses──► CreateUserRequest
                                │
                                │ truyền thẳng xuống
                                ▼
                         ICreateUserUseCase ← phải import CreateUserRequest
                                              (thuộc api-portal module!)
```

Điều này phá vỡ **Hexagonal Architecture** vì:

| Layer | Trách nhiệm | Không được biết về |
|---|---|---|
| **API (api-portal)** | Nhận HTTP request, validate input | Logic nghiệp vụ |
| **Application (user-service)** | Xử lý business logic | HTTP, REST, validation annotations |

---

## Luồng thực tế trong code

```
HTTP POST /api/users
{username, email, password}
        │
        ▼
CreateUserRequest          ← có @NotBlank, @Email, @Size
        │
        │ userApiMapper.toCommand(request)
        ▼
CreateUserCommandDto        ← thuần túy, không có gì của HTTP
        │
        │ createUserUseCase.execute(command)
        ▼
  Business Logic
```

---

## Tóm lại — Mapper giải quyết 2 việc

| Vấn đề | Giải pháp của Mapper |
|---|---|
| **Tách biệt layer** | API layer và Application layer dùng object riêng, không phụ thuộc nhau |
| **Dễ thay đổi** | Thay đổi `CreateUserRequest` (thêm field mới cho HTTP) không ảnh hưởng đến UseCase |

> **Ngắn gọn:** Mapper là "phiên dịch viên" giữa 2 thế giới — thế giới HTTP (API) và thế giới Business Logic (Application). Nó giữ cho 2 thế giới này độc lập với nhau.

---

## Các file liên quan

| File | Layer | Vai trò |
|---|---|---|
| `user/request/CreateUserRequest.java` | API | Nhận input từ HTTP, có validation annotations |
| `user/mapper/UserApiMapper.java` | API | Chuyển đổi Request → CommandDto |
| `user-service/.../CreateUserCommandDto.java` | Application | Command thuần túy gửi vào UseCase |
| `user-service/.../ICreateUserUseCase.java` | Application | Xử lý business logic |
