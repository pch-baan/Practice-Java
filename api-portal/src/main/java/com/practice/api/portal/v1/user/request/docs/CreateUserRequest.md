# CreateUserRequest — HTTP Request DTO cho API tạo User

## Mục đích

`CreateUserRequest` là lớp DTO nhận dữ liệu từ client gửi lên qua HTTP request body.
Nó đại diện cho **API input contract** — tức là "cam kết" của server với client về
cấu trúc dữ liệu đầu vào được chấp nhận, kèm theo quy tắc validation ngay tại rìa ngoài.

---

## Storytelling

> **CreateUserRequest** = tờ đơn đăng ký mà khách hàng điền trước khi gặp nhân viên.
>
> Trước khi tờ đơn được chuyển vào bên trong để xử lý, bảo vệ tại cửa (Bean Validation)
> sẽ kiểm tra sơ bộ: "Bạn đã điền đủ họ tên chưa? Email hợp lệ không? Mật khẩu đủ dài chưa?"
>
> Nếu tờ đơn thiếu thông tin → trả lại ngay, không cho vào trong.
> Nếu hợp lệ → chuyển tiếp cho nhân viên lễ tân (Controller) xử lý.

---

## Luồng dữ liệu

```
HTTP Client
  │  POST /api/v1/users
  │  Body: { "username": "...", "email": "...", "password": "..." }
  ▼
Bean Validation (@Valid)   ← kiểm tra annotation trên CreateUserRequest
  │  thất bại → 400 Bad Request + error message
  │  thành công ↓
CreateUserRequest (HTTP DTO)   ←── file này
  │
  ▼
UserApiMapper.toCommand()
  │  chuyển đổi sang Application DTO
  ▼
CreateUserCommand
  │
  ▼
ICreateUserUseCase.execute()
```

---

## Giải thích từng validation annotation

```java
public record CreateUserRequest(

    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username,
    //  ↑ @NotBlank: không được null, rỗng, hoặc chỉ toàn khoảng trắng
    //  ↑ @Size: độ dài từ 3 đến 50 ký tự

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Invalid email format")
    String email,
    //  ↑ @Email: phải đúng định dạng xxx@xxx.xxx

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
    //  ↑ @Size(min = 8): mật khẩu tối thiểu 8 ký tự
) {}
```

---

## Tại sao validate ở đây thay vì trong UseCase?

```
CreateUserRequest      CreateUserCommand       User (Domain)
(HTTP layer)           (Application layer)     (Domain layer)
──────────────         ───────────────────     ──────────────────
Validate format        Validate business       Enforce invariants
  - NotBlank             rule (nếu có)           - domain rule
  - Email format         - uniqueness check
  - Size                   (gọi repository)
```

> **Nguyên tắc:** validate sớm nhất có thể — lỗi format không cần đi sâu vào domain mới biết.
> `CreateUserRequest` chặn các input sai định dạng ngay tại rìa ngoài,
> giúp UseCase và Domain tập trung vào business logic thuần túy.

---

## Tại sao cần tách `CreateUserRequest` khỏi `CreateUserCommand`?

```
CreateUserRequest                  CreateUserCommand
(HTTP DTO)                         (Application DTO)
─────────────────────              ─────────────────────────────
Thuộc về api-portal                Thuộc về user-service/application
Có annotation @Valid, @NotBlank    Không có annotation HTTP/validation
Gắn với HTTP/JSON contract         Gắn với business flow
Thay đổi theo API version          Thay đổi theo business requirement
```

> Nếu dùng chung: thêm field validation cho HTTP sẽ làm "bẩn" Application layer,
> vi phạm nguyên tắc tách biệt trách nhiệm (Separation of Concerns).

---

## Vai trò trong kiến trúc

```
[ HTTP Client ]
      │  gửi JSON body
      ▼
[ @Valid + CreateUserRequest ]  ← HTTP input contract (api-portal)
      │  validation pass
      ▼
[ UserApiMapper ]               ← chuyển đổi sang Command
      │
      ▼
[ ICreateUserUseCase ]          ← Application port
```

---

## Tóm tắt

| Câu hỏi | Trả lời |
|---|---|
| `CreateUserRequest` là gì? | HTTP request DTO — cấu trúc JSON nhận từ client |
| Nằm ở layer nào? | `api-portal` — inbound adapter |
| Ai dùng nó? | `UserController` nhận, `UserApiMapper` chuyển đổi sang `CreateUserCommand` |
| Validation xảy ra ở đâu? | Tại Controller với `@Valid`, Bean Validation tự động kiểm tra các annotation |
| Vì sao dùng `record`? | Immutable, ngắn gọn, rõ ý định chỉ chứa data đầu vào |
| Vì sao không truyền thẳng vào UseCase? | Tách HTTP contract khỏi Application layer — thay đổi độc lập nhau |
