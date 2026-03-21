# UserApiMapper — Cầu nối giữa HTTP layer và Application layer

## Mục đích

`UserApiMapper` chịu trách nhiệm **chuyển đổi dữ liệu** giữa hai thế giới:
- **HTTP world** (`CreateUserRequest`, `UserResponse`) — thuộc `api-portal`
- **Application world** (`CreateUserCommandDto`, `UserResponseDto`) — thuộc `user-service`

Mapper đảm bảo hai layer này không phụ thuộc trực tiếp vào nhau,
có thể thay đổi độc lập mà không làm vỡ bên còn lại.

---

## Storytelling

> **UserApiMapper** = phiên dịch viên tại cửa khẩu.
>
> Khách nước ngoài (HTTP client) nói tiếng Anh (JSON request).
> Nhân viên bên trong (UseCase) chỉ hiểu tiếng nội bộ (Command/Dto).
>
> Phiên dịch viên ngồi ở cửa khẩu (api-portal):
> - Chiều vào: dịch `CreateUserRequest` → `CreateUserCommandDto`
> - Chiều ra: dịch `UserResponseDto` → `UserResponse`
>
> Khách không cần biết nội bộ nói gì.
> Nội bộ không cần biết khách nói tiếng gì.
> Phiên dịch viên lo hết.

---

## Hai phương thức

```
┌──────────────────────────────────────────────────────────┐
│                      UserApiMapper                       │
│                                                          │
│  toCommand()                      toResponse()           │
│                                                          │
│  CreateUserRequest   ──────►  CreateUserCommandDto       │
│  (HTTP input DTO)             (Application input DTO)    │
│                                                          │
│  UserResponseDto     ──────►  UserResponse               │
│  (Application output DTO)    (HTTP output DTO)           │
└──────────────────────────────────────────────────────────┘
```

---

## Code giải thích từng bước

```java
// Chiều VÀO: HTTP → Application
public CreateUserCommandDto toCommand(CreateUserRequest request) {
    return new CreateUserCommandDto(
        request.username(),   // lấy từ HTTP DTO
        request.email(),      // lấy từ HTTP DTO
        request.password()    // lấy từ HTTP DTO
    );
    // Kết quả: Application DTO — không có gì liên quan đến HTTP nữa
}

// Chiều RA: Application → HTTP
public UserResponse toResponse(UserResponseDto dto) {
    return new UserResponse(
        dto.id(),
        dto.username(),
        dto.email(),
        dto.role(),
        dto.status(),
        dto.createdAt()
    );
    // Kết quả: HTTP DTO — sẵn sàng serialize thành JSON trả về client
}
```

---

## Vị trí trong luồng xử lý đầy đủ

```
HTTP Client
    │  POST /api/v1/users  { username, email, password }
    ▼
UserController
    │
    ├─► userApiMapper.toCommand(request)
    │         CreateUserRequest  →  CreateUserCommandDto
    │
    ├─► createUserUseCase.execute(command)
    │         (xử lý business logic bên trong user-service)
    │         trả về UserResponseDto
    │
    ├─► userApiMapper.toResponse(dto)
    │         UserResponseDto  →  UserResponse
    │
    ▼
HTTP Client
    │  201 Created  { id, username, email, role, status, createdAt }
```

---

## Tại sao không để Controller tự map thủ công?

```java
// ❌ Cách sai — Controller làm quá nhiều việc
@PostMapping
public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
    var command = new CreateUserCommandDto(request.username(), request.email(), request.password());
    var dto = createUserUseCase.execute(command);
    var response = new UserResponse(dto.id(), dto.username(), ...);  // lặp lại ở mọi endpoint
    return ResponseEntity.status(201).body(response);
}

// ✅ Cách đúng — tách ra UserApiMapper
var command  = userApiMapper.toCommand(request);
var dto      = createUserUseCase.execute(command);
var response = userApiMapper.toResponse(dto);
```

| Vấn đề nếu không tách | Lợi ích khi có Mapper |
|---|---|
| Logic mapping lặp lại ở nhiều endpoint | Tập trung một chỗ, dễ bảo trì |
| Controller biết quá nhiều về cấu trúc DTO | Controller chỉ biết gọi mapper |
| Khó test mapping logic riêng biệt | Có thể unit test mapper độc lập |
| Thêm field mới → sửa nhiều nơi | Chỉ sửa mapper |

---

## Tại sao không dùng MapStruct ở đây?

Mapper này viết tay vì:
- Số field ít, mapping 1-1 đơn giản
- Không cần generate code phức tạp
- Dễ đọc, dễ hiểu hơn khi học

> Trong production lớn hơn, MapStruct sẽ tự generate code này,
> tránh lỗi typo và giảm boilerplate.

---

## Vai trò trong kiến trúc Hexagonal

```
[ api-portal ]                      [ user-service ]

  CreateUserRequest                   CreateUserCommandDto
       │                                      ▲
       └──── UserApiMapper.toCommand() ───────┘

  UserResponse                        UserResponseDto
       ▲                                      │
       └──── UserApiMapper.toResponse() ──────┘
```

`UserApiMapper` nằm trong `api-portal` — đây là **inbound adapter**.
Nó biết cả hai phía nhưng sự phụ thuộc chỉ đi một chiều:
`api-portal` phụ thuộc vào `user-service`, không bao giờ ngược lại.

---

## Tóm tắt

| Câu hỏi | Trả lời |
|---|---|
| `UserApiMapper` làm gì? | Chuyển đổi DTO giữa HTTP layer và Application layer |
| Nằm ở layer nào? | `api-portal` — inbound adapter |
| Có mấy phương thức? | 2: `toCommand()` (vào) và `toResponse()` (ra) |
| Ai gọi nó? | `UserController` |
| Vì sao cần tách ra? | Tập trung mapping logic, Controller gọn hơn, dễ test riêng |
| Vì sao không dùng MapStruct? | Field ít, mapping đơn giản, ưu tiên dễ đọc khi học |
