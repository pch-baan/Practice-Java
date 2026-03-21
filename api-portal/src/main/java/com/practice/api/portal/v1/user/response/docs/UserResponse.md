# UserResponse — HTTP Response DTO cho User API

## Mục đích

`UserResponse` là lớp DTO dùng để **trả dữ liệu user về cho client** qua HTTP response body.
Nó đại diện cho **API contract** — tức là "cam kết" của server với client về cấu trúc dữ liệu trả về.

---

## Storytelling

> **UserResponse** = tờ phiếu kết quả mà ngân hàng đưa lại cho khách sau khi xử lý xong.
>
> Khi khách mở tài khoản thành công, nhân viên lễ tân (Controller) không đưa toàn bộ
> hồ sơ nội bộ cho khách xem — họ chỉ in ra một tờ phiếu gồm các thông tin cần thiết:
> số tài khoản, tên, email, trạng thái.
>
> Mật khẩu? Dữ liệu nhạy cảm? Không có trong tờ phiếu đó.

---

## Luồng dữ liệu

```
UseCase
  │  trả về UserResponseDto (Application DTO)
  ▼
UserApiMapper.toResponse()
  │  chuyển đổi sang HTTP DTO
  ▼
UserResponse (HTTP DTO)  ←── file này
  │  được serialize thành JSON
  ▼
HTTP Client
  {
    "id": "...",
    "username": "...",
    "email": "...",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "..."
  }
```

---

## Tại sao dùng `record`?

```java
public record UserResponse(
    UUID id,
    String username,
    String email,
    UserRoleEnum role,
    UserStatusEnum status,
    LocalDateTime createdAt
) {}
```

| Đặc điểm | Lý do chọn `record` |
|---|---|
| **Immutable** | Response DTO không cần thay đổi sau khi tạo |
| **Ngắn gọn** | Tự sinh constructor, getter, equals, hashCode, toString |
| **Rõ ý định** | `record` = "tôi chỉ chứa data, không có logic" |

---

## Tại sao cần tách `UserResponse` khỏi `UserResponseDto`?

```
UserResponseDto                    UserResponse
(Application DTO)                  (HTTP DTO)
─────────────────────              ─────────────────────────
Thuộc về user-service              Thuộc về api-portal
Gắn với business logic             Gắn với HTTP/JSON contract
UseCase trả về                     Controller trả về cho client
Có thể thay đổi theo domain        Có thể thay đổi theo API version
```

> Nếu dùng chung một DTO: thay đổi business logic sẽ vô tình thay đổi API contract,
> hoặc ngược lại — ảnh hưởng đến tất cả client đang consume API.

---

## Vai trò trong kiến trúc

```
[ UserController ]
      │
      │  gọi UseCase → nhận UserResponseDto
      │  gọi mapper  → chuyển thành UserResponse
      │
      ▼
[ UserResponse ]  ← HTTP contract, nằm ở rìa ngoài (api-portal)
      │
      ▼
[ HTTP Client ]   ← JSON response
```

`UserResponse` nằm ở **rìa ngoài cùng** của kiến trúc Hexagonal —
đúng nguyên tắc: domain không biết gì về HTTP, HTTP DTO không chứa business logic.

---

## Tóm tắt

| Câu hỏi | Trả lời |
|---|---|
| `UserResponse` là gì? | HTTP response DTO — cấu trúc JSON trả về cho client |
| Nằm ở layer nào? | `api-portal` — inbound adapter |
| Ai tạo ra nó? | `UserApiMapper.toResponse()` từ `UserResponseDto` |
| Vì sao không dùng thẳng domain `User`? | Tránh lộ thông tin nhạy cảm, tách HTTP contract khỏi domain |
| Vì sao dùng `record`? | Immutable, ngắn gọn, rõ ý định chỉ chứa data |
