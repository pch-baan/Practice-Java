# Giải thích chi tiết `UserResponseDto`

## Toàn bộ file

```java
public record UserResponseDto(
        UUID id,
        String username,
        String email,
        UserRoleEnum role,
        UserStatusEnum status,
        LocalDateTime createdAt
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUsername().getValue(),  // ← tại sao .getValue()?
                user.getEmail().getValue(),     // ← tại sao .getValue()?
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
```

---

## Phần 1: Các field và kiểu dữ liệu

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | `UUID` | ID duy nhất, dạng `550e8400-e29b-41d4-a716...` thay vì số `1,2,3` |
| `username` | `String` | Tên người dùng thuần túy |
| `email` | `String` | Email thuần túy |
| `role` | `UserRoleEnum` | `USER` hoặc `ADMIN` |
| `status` | `UserStatusEnum` | `ACTIVE` hoặc `INACTIVE` |
| `createdAt` | `LocalDateTime` | Thời điểm tạo account |

**Lưu ý quan trọng:** `UserResponseDto` **không có** `password` — bảo mật, không bao giờ trả password về client!

---

## Phần 2: Static Factory Method `from(User user)`

Thay vì để Mapper bên ngoài tự lấy từng field:

```
Cách thông thường (Mapper bên ngoài làm):
UserApiMapper → lấy từng field của User → tạo UserResponseDto

Cách này (Factory Method):
UserResponseDto.from(user) → tự biết cách tạo từ User
```

```
┌──────────────────────┐        .from(user)       ┌──────────────────────┐
│   User (Domain)      │  ───────────────────────► │  UserResponseDto     │
│                      │                           │                      │
│  id: UUID            │                           │  id: UUID            │
│  username: UsernameVO│ → .getValue() → String    │  username: String    │
│  email: EmailVO      │ → .getValue() → String    │  email: String       │
│  role: UserRoleEnum  │ ──────────────────────►   │  role: UserRoleEnum  │
│  status: UserStatus  │ ──────────────────────►   │  status: UserStatus  │
│  createdAt: DateTime │ ──────────────────────►   │  createdAt: DateTime │
│  passwordHash: ...   │ ✗ KHÔNG lấy (bảo mật)     │                      │
└──────────────────────┘                           └──────────────────────┘
```

---

## Phần 3: Tại sao phải `.getValue()`?

Trong `User`, `username` và `email` **không phải** `String` thông thường — chúng là **Value Object (VO)**:

```java
// Bên trong User.java
private UsernameVO username;  // ← không phải String!
private EmailVO email;        // ← không phải String!
```

`UsernameVO` là một class wrapper có validation bên trong:

```
UsernameVO {
    private final String value;  ← String thật nằm ở đây

    // Khi tạo, tự validate:
    // - không được blank
    // - min 3, max 50 ký tự
}
```

Nên muốn lấy String ra phải gọi `.getValue()`:

```java
user.getUsername()            // → trả về UsernameVO (object)
user.getUsername().getValue() // → trả về String "john"
```

---

## Toàn bộ luồng từ đầu đến cuối

```
POST /api/users
     │
     ▼
CreateUserRequest (record)
     │ userApiMapper.toCommand()
     ▼
CreateUserCommandDto (record)
     │ createUserUseCase.execute()
     ▼
 UseCase xử lý → tạo User (Domain Object)
     │
     │ UserResponseDto.from(user)
     ▼
UserResponseDto (record)  ← file đang xét
     │
     ▼
HTTP Response 201 Created
{
  "id": "550e8400-...",
  "username": "john",
  "email": "john@gmail.com",
  "role": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-03-13T10:00:00"
}
```

---

## Tóm tắt

| Khái niệm | Giải thích |
|---|---|
| `record` | Chỉ chứa data, tự sinh constructor/getter |
| Các field | Đủ thông tin trả về client, **bỏ password** |
| `from(User user)` | Factory method — DTO tự biết cách convert từ Domain |
| `.getValue()` | Vì username/email là Value Object, cần lấy String bên trong ra |
