# UserController — HTTP Entry Point cho User Management

## Mục đích

`UserController` là lớp ngoài cùng trong kiến trúc — nhận HTTP request từ client,
dịch sang ngôn ngữ của application layer, và trả về HTTP response.

---

## Storytelling

> **Controller** = nhân viên lễ tân tại ngân hàng.
>
> Khách hàng (HTTP client) bước vào và nói: "Tôi muốn mở tài khoản".
> Lễ tân không tự làm mọi thứ — họ điền form nội bộ (Command),
> chuyển cho bộ phận xử lý (UseCase), rồi nhận kết quả và thông báo lại cho khách (Response).
>
> Lễ tân **không biết** cách lưu dữ liệu vào database, không biết mật khẩu được hash như thế nào.
> Họ chỉ biết: nhận yêu cầu → chuyển tiếp → trả kết quả.

---

## Luồng xử lý

```
HTTP Client
    │
    │  POST /api/v1/users
    │  Body: { username, email, password }
    ▼
┌─────────────────────────────────────┐
│           UserController            │
│                                     │
│  1. CreateUserRequest (HTTP DTO)    │
│         ↓ userApiMapper.toCommand() │
│  2. CreateUserCommand               │
│         ↓ createUserUseCase.execute │
│  3. UserResponseDto (App DTO)       │
│         ↓ userApiMapper.toResponse()│
│  4. UserResponse (HTTP DTO)         │
└─────────────────────────────────────┘
    │
    │  201 Created
    │  Body: { id, username, email, ... }
    ▼
HTTP Client
```

---

## Code giải thích từng bước

```java
@PostMapping
public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {

    // Bước 1: HTTP request → Command
    // UserApiMapper dịch CreateUserRequest (HTTP contract) sang CreateUserCommand (Application contract)
    // Controller không bao giờ truyền thẳng HTTP DTO vào UseCase
    var command = userApiMapper.toCommand(request);

    // Bước 2: Gọi UseCase qua port interface
    // Controller chỉ biết ICreateUserUseCase (interface), không biết implementation cụ thể
    // → Loose coupling: có thể swap implementation mà không sửa Controller
    var appDto = createUserUseCase.execute(command);

    // Bước 3: Application DTO → HTTP response
    // Tách biệt HTTP contract ra khỏi Application layer
    // UserResponse có thể thêm/bớt field mà không ảnh hưởng đến UseCase
    var response = userApiMapper.toResponse(appDto);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

---

## Tại sao cần 2 lớp DTO riêng biệt?

```
CreateUserRequest          CreateUserCommand
(HTTP DTO)                 (Application DTO)
──────────────────         ──────────────────────────────
Thuộc về api-portal        Thuộc về user-service/application
Gắn với HTTP contract      Gắn với business logic
Có @Valid annotation        Không có annotation HTTP nào
Có thể có field "extra"    Chỉ chứa đúng thứ UseCase cần
```

> Nếu dùng chung 1 DTO: thay đổi field trong HTTP request sẽ làm vỡ UseCase,
> hoặc ngược lại — business logic thay đổi sẽ ảnh hưởng đến API contract.

---

## Vai trò trong kiến trúc Hexagonal

```
[ HTTP Client ]
      │
      ▼
[ UserController ]  ← Inbound Adapter (api-portal)
      │
      ▼
[ ICreateUserUseCase ]  ← Port (user-service/application)
      │
      ▼
[ CreateUserUseCaseImpl ]  ← Use Case (user-service/application)
      │
      ▼
[ IUserRepository ]  ← Port (user-service/domain)
      │
      ▼
[ UserPostgresqlAdapter ]  ← Outbound Adapter (user-service/infrastructure)
```

Controller nằm ở rìa ngoài cùng — đúng với nguyên tắc Hexagonal:
**phụ thuộc hướng vào trong**, không bao giờ ngược lại.

---

## Tóm tắt

| Câu hỏi | Trả lời |
|---|---|
| Controller làm gì? | Nhận HTTP, dịch sang Command, gọi UseCase, trả HTTP response |
| Controller biết gì về DB? | Không biết gì |
| Controller biết gì về UseCase cụ thể? | Không — chỉ biết interface `ICreateUserUseCase` |
| Vì sao cần `UserApiMapper`? | Tách HTTP contract khỏi Application contract |
| Endpoint | `POST /api/v1/users` → `201 Created` |
