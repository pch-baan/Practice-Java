# `record` trong Java là gì?

## Câu chuyện: Tờ phiếu đặt hàng

Tưởng tượng bạn có một **tờ phiếu đặt hàng** — nó chỉ dùng để **ghi thông tin**, không làm gì khác:

```
┌─────────────────────────────┐
│       PHIẾU ĐẶT HÀNG        │
├─────────────────────────────┤
│  Username : john            │
│  Email    : john@gmail.com  │
│  Password : 12345678        │
└─────────────────────────────┘
   ← Chỉ chứa data, không làm gì thêm
```

Trước Java 16, để tạo class như vậy, bạn phải viết **rất nhiều boilerplate**:

```java
// Cách cũ — class thường
public class CreateUserRequest {
    private final String username;
    private final String email;
    private final String password;

    // Constructor
    public CreateUserRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Getters
    public String username() { return username; }
    public String email()    { return email; }
    public String password() { return password; }

    // equals, hashCode, toString
    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
    @Override public String toString() { ... }
}
```

**`record` sinh ra để giải quyết vấn đề này** — viết gọn hết trong 1 dòng:

```java
// Cách mới — record
public record CreateUserRequest(String username, String email, String password) {}
```

---

## Record tự động tạo ra những gì?

```
public record CreateUserRequest(String username, String email, String password) {}
                │
                │ Java tự sinh ra:
                ▼
        ✅ Constructor(username, email, password)
        ✅ Getter: username(), email(), password()
        ✅ equals() — so sánh theo giá trị
        ✅ hashCode()
        ✅ toString() — in ra đẹp
```

---

## Đặc điểm quan trọng của `record`

| Đặc điểm | Ý nghĩa |
|---|---|
| **Immutable** | Sau khi tạo xong, **không thể sửa** các field — không có setter |
| **Data carrier** | Chỉ dùng để **mang data** từ nơi này sang nơi khác |
| **Ngắn gọn** | Thay thế hoàn toàn class POJO/DTO thông thường |

---

## Trong project của bạn

```java
// CreateUserRequest — record nhận data từ HTTP body
public record CreateUserRequest(
    String username,
    String email,
    String password
) {}

// CreateUserCommandDto — record mang data vào UseCase
public record CreateUserCommandDto(
    String username,
    String email,
    String password
) {}
```

Cả 2 đều là "tờ phiếu" — chỉ **chứa data**, không xử lý logic gì. `record` là lựa chọn hoàn hảo cho **DTO (Data Transfer Object)**.

> **Một câu:** `record` = class chỉ chứa data, Java tự viết hộ constructor + getter + equals + toString.
