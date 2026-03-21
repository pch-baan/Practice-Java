# Value Object (VO) trong Domain Model

## Vấn đề nếu dùng `String` thô

```java
// ❌ Không có VO — String thô
public class User {
    private String username;
    private String email;

    public void setUsername(String username) {
        this.username = username; // "   ", null, "a!@#$%" đều qua được!
    }
}
```

`String` chỉ là một cái hộp rỗng — ai muốn bỏ gì vào cũng được. Không có ai canh cửa.

---

## VO là "người canh cửa"

```
  Raw Input              VO (Factory)              Valid Object
──────────────       ──────────────────────       ──────────────
"  ABC@GMAIL.COM "  →  EmailVO.of(raw)        →   EmailVO("abc@gmail.com")
"invalid-email"     →  ❌ throw Exception
null                →  ❌ throw Exception
```

Nhìn vào `EmailVO.java`:

```java
public static EmailVO of(String raw) {
    if (raw == null || raw.isBlank())           // canh cửa 1: null/blank
        throw new UserDomainException("...");
    String trimmed = raw.trim().toLowerCase();  // chuẩn hóa luôn
    if (!trimmed.matches("^[\\w.+-]+@..."))    // canh cửa 2: format
        throw new UserDomainException("...");
    return new EmailVO(trimmed);               // chỉ tạo được khi hợp lệ
}
```

**Quy tắc vàng:** Nếu một `EmailVO` object tồn tại → nó **chắc chắn hợp lệ**. Không cần validate lại ở nơi khác.

---

## 3 lý do cụ thể

### 1. Validation tập trung 1 chỗ

```java
// Validation logic nằm trong EmailVO.of() — không rải khắp nơi
user.updateEmail("new@email.com");  // EmailVO.of() tự validate bên trong
```

### 2. Immutable (bất biến)

```java
public final class EmailVO {        // không extend được
    private final String value;     // không thay đổi sau khi tạo
    private EmailVO(String value)   // constructor private, chỉ qua .of()
```

Sau khi tạo xong, không ai có thể sửa giá trị bên trong → an toàn để dùng chung.

### 3. Type nói lên ý nghĩa

```java
// ❌ 3 cái String, ai biết cái nào là gì?
public User reconstruct(String a, String b, String c, ...)

// ✅ Type tự giải thích
public User reconstruct(UUID id, UsernameVO username, EmailVO email, PasswordHashVO hash, ...)
```

---

## Tóm tắt

| Nếu dùng `String`                             | Nếu dùng `VO`                          |
|-----------------------------------------------|----------------------------------------|
| Validate rải khắp nơi (service, controller...) | Validate 1 lần trong `VO.of()`         |
| Có thể gán giá trị rác bất cứ lúc nào         | Object tồn tại = đã hợp lệ             |
| Type không nói lên ý nghĩa                    | `EmailVO` rõ hơn `String`              |
| Phải nhớ trim/lowercase ở nhiều chỗ           | VO tự chuẩn hóa trong factory          |

> **Một câu:** VO là wrapper quanh primitive value, đảm bảo rằng **chỉ giá trị hợp lệ mới tồn tại được** trong domain.
