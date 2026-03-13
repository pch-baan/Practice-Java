# Tại sao UserCredential dùng `record` thay vì `class`?

## Cốt lõi: Entity vs Value Object trong DDD

Trong DDD, có 2 loại domain object:

| | Entity | Value Object |
|---|---|---|
| **Identity** | Có UUID id riêng | Không có identity riêng |
| **Equality** | So sánh bằng ID | So sánh bằng TẤT CẢ fields |
| **Lifecycle** | Có (create → change → delete) | Không — immutable từ đầu |
| **Business methods** | Có (thay đổi state) | Không — chỉ là data carrier |
| **Ví dụ trong project** | `RefreshToken`, `User` | `UserCredential`, `EmailVO` |

---

## UserCredential là gì thực chất?

`UserCredential` **không phải** là User aggregate. Nó là **read model** —
một snapshot dữ liệu load từ DB để auth-service dùng tạm, không bao giờ persist lại.

```
users table (owned by user-service)
        │
        │  SELECT id, username, password_hash, role, status
        ▼
UserCredential  ← chỉ đọc, xài xong bỏ, không lưu lại
```

Nó KHÔNG có:
- `activate()` / `deactivate()` — auth-service không quản lý vòng đời user
- `changePassword()` — không phải trách nhiệm của auth-service
- Identity-based equality — 2 UserCredential cùng data là như nhau về logic

---

## Tại sao record phù hợp?

Java `record` sinh ra chính xác cho trường hợp này — immutable data carrier:

```java
public record UserCredential(
    UUID userId,
    String username,
    String passwordHash,
    String role,
    String status
) {}

// Java tự generate:
// - Canonical constructor
// - userId(), username(), passwordHash(), role(), status()  ← accessor methods
// - equals() dựa trên TẤT CẢ fields
// - hashCode() dựa trên TẤT CẢ fields
// - toString()
```

---

## Tại sao RefreshToken KHÔNG thể dùng record?

`RefreshToken` là Entity — có `revoke()` thay đổi state. Record không cho phép mutate fields:

```java
// RefreshToken — CẦN mutate state → KHÔNG thể dùng record
public class RefreshToken {
    private boolean revoked;  // ← mutable field

    public void revoke() {
        this.revoked = true;  // ← record cấm điều này (fields là final)
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
```

---

## Bảng so sánh trong project

| | `UserCredential` | `RefreshToken` |
|---|---|---|
| **DDD type** | Read Model / Value Object | Entity |
| **UUID có phải identity?** | `userId` là FK — identity của User, không phải của credential | `id` là identity thực sự của token |
| **Mutable state** | Không | Có (`revoked`) |
| **Business methods** | Không | Có (`revoke()`, `isValid()`, `isExpired()`) |
| **Java type** | `record` | `class` |

---

## Kết luận

> `UserCredential` chỉ là "tờ giấy ghi thông tin user" —
> auth-service mượn đọc để xác thực, không sở hữu, không thay đổi.
> `record` diễn đạt đúng bản chất đó: immutable, value-based equality, zero boilerplate.
