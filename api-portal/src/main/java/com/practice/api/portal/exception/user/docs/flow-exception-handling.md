# Flow xử lý Exception — UserConflictException → 409 Conflict

## Ví dụ thực tế

Người dùng gửi request tạo tài khoản với email đã tồn tại trong hệ thống.

```
POST /api/v1/users  { email: "hieu@gmail.com" }  ← email đã tồn tại
```

---

## Flow đầy đủ

```
POST /api/v1/users  { email: "hieu@gmail.com" }
         │
         ▼
┌─────────────────────────┐
│     UserController      │  createUser() gọi createUserUseCase.execute(command)
│     (api-portal/v1)     │
└────────────┬────────────┘
             │ execute()
             ▼
┌─────────────────────────────┐
│   CreateUserUseCaseImpl     │  gọi userDomainService.validateUniqueConstraints(...)
│   (application layer)       │
└────────────┬────────────────┘
             │ validateUniqueConstraints()
             ▼
┌─────────────────────────────┐
│     UserDomainService       │  existsByEmail() → true
│     (domain layer)          │  → throw new UserConflictException("Email already exists")
└────────────┬────────────────┘
             │ 💥 Exception bay ngược lên stack
             │ (không ai catch ở UseCase hay Controller)
             ▼
┌─────────────────────────────────┐
│      UserExceptionHandler       │  Spring tự động bắt!
│  @ExceptionHandler(             │  handleUserConflict(ex) được gọi
│    UserConflictException.class) │
└────────────┬────────────────────┘
             │
             ▼
   HTTP 409 Conflict
   {
     "status": 409,
     "message": "Email already exists: hieu@gmail.com",
     "timestamp": "2026-03-21T..."
   }
```

---

## Chìa khóa để hiểu: "Không ai catch = Spring tự xử lý"

`UserController` không có `try-catch`. Exception bay xuyên qua Controller mà không bị bắt
→ Spring MVC nhận exception đó → tìm `@ExceptionHandler` khớp → gọi `UserExceptionHandler`.

```java
// UserController KHÔNG có try-catch
public ResponseEntity<UserResponse> createUser(...) {
    var command = userApiMapper.toCommand(request);
    var appDto = createUserUseCase.execute(command);  // ← nếu throw ở đây
    // ... dòng này không bao giờ chạy nếu throw
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

---

## Câu thần chú

> **"Domain `throw` → Controller không catch → Spring tự tìm Handler → Handler trả HTTP response"**

---

## Bảng tóm tắt

| Ai làm gì | Code ở đâu |
|---|---|
| Phát hiện lỗi business | `UserDomainService.java` — `validateUniqueConstraints()` |
| Ném exception | `throw new UserConflictException(...)` |
| Không catch gì cả | `UserController`, `CreateUserUseCaseImpl` |
| Bắt và chuyển thành HTTP | `UserExceptionHandler` — `handleUserConflict()` |

---

## Các loại exception và HTTP status tương ứng

| Exception | HTTP Status | Ý nghĩa |
|---|---|---|
| `UserConflictException` | `409 Conflict` | Email hoặc username đã tồn tại |
| `UserNotFoundException` | `404 Not Found` | Không tìm thấy user |
| `UserDomainException` | `400 Bad Request` | Lỗi business logic chung |

---

## Q&A

### Q: "Exception bay ngược lên stack" nghĩa là gì?

Các method gọi nhau như **chồng đĩa**. Khi `throw` xảy ra, Java không chạy tiếp — exception được ném ngược lên từng tầng:

```
[UserDomainService]        → throw UserConflictException
           ↑ bay lên
[CreateUserUseCaseImpl]    → không catch → tiếp tục bay lên
           ↑ bay lên
[UserController]           → không catch → tiếp tục bay lên
           ↑ bay lên
[Spring MVC Framework]     → tìm @ExceptionHandler → bắt!
```

---

### Q: Vậy có nên dùng try-catch ở Controller không?

**Không nên**, vì `@ExceptionHandler` sinh ra để thay thế việc đó:

```java
// ❌ SAI — catch xong làm gì? Đây chính là việc của UserExceptionHandler rồi!
public ResponseEntity<UserResponse> createUser(...) {
    try {
        var appDto = createUserUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (UserConflictException ex) {
        return ResponseEntity.status(409)...
    }
}

// ✅ ĐÚNG — để exception bay lên, UserExceptionHandler lo
public ResponseEntity<UserResponse> createUser(...) {
    var appDto = createUserUseCase.execute(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

| Khi nào dùng try-catch | Khi nào để bay lên |
|---|---|
| Muốn **tự xử lý** lỗi (retry, fallback, log rồi tiếp tục) | Lỗi cần **chuyển thành HTTP response** |
| Lỗi chỉ xảy ra **nội bộ**, không cần báo ra ngoài | Đã có `@ExceptionHandler` lo rồi |

> **Câu thần chú:** Dùng cả `@ExceptionHandler` lẫn try-catch cho cùng một lỗi = làm việc gấp đôi.
