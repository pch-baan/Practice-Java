# Q&A — `equals()` trong User.java

---

## Q: What is the purpose of the `equals()` method?

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User other)) return false;
    return id.equals(other.id);
}
```

**Mục đích:** Override `equals()` để so sánh 2 User **theo UUID id**, không theo địa chỉ RAM.

- Java mặc định so sánh địa chỉ RAM → 2 User cùng id load từ DB sẽ bị coi là "khác nhau" ❌
- Sau khi override → so sánh đúng theo nghĩa business: cùng id = cùng 1 người ✅
- Đây là nguyên tắc **DDD Entity**: identity dựa trên ID, không phải giá trị các field.

---

## Q: What is `Object o`?

`equals()` là method **kế thừa từ `java.lang.Object`** — class cha của mọi class Java.
Signature bắt buộc là `equals(Object o)`, không được đổi thành `equals(User o)`.

- Nếu viết `equals(User o)` → Java coi là **overload** (method mới), không phải **override**
  → `equals()` mặc định vẫn so sánh địa chỉ RAM.
- Vì nhận `Object`, `o` có thể là bất cứ thứ gì: String, Integer, null, User...
  → Cần dòng `instanceof` để lọc.

---

## Q: What is `this == o`?

```java
if (this == o) return true;
```

- `this` = object đang gọi method (`u1.equals(...)` → `this` là `u1`)
- `o` = tham số truyền vào
- `==` so sánh **địa chỉ RAM** (reference), không phải giá trị

**Mục đích:** Tối ưu hiệu năng (short-circuit).
Nếu `this` và `o` trỏ vào cùng 1 object trong RAM → chắc chắn bằng nhau → return `true` ngay,
không cần thực hiện thêm bước nào.

```
u1.equals(u1)  →  this == o  →  0x01 == 0x01  →  true  (return ngay)
u1.equals(u2)  →  this == o  →  0x01 == 0x02  →  false (tiếp tục kiểm tra)
```

---

## Q: What is `!(o instanceof User other)`?

```java
if (!(o instanceof User other)) return false;
```

3 thành phần:

| Thành phần | Ý nghĩa |
|---|---|
| `instanceof User` | Kiểm tra `o` có phải kiểu `User` không |
| `User other` | Nếu đúng, cast `o` thành `User` và đặt tên `other` — **Pattern Matching Java 16+** |
| `!` | Lật điều kiện: nếu `o` KHÔNG phải `User` → return false |

**Cách cũ (trước Java 16):**
```java
if (!(o instanceof User)) return false;
User other = (User) o;  // cast thủ công
```

**Cách mới (Java 16+):**
```java
if (!(o instanceof User other)) return false;
// "other" đã được cast tự động, dùng luôn ở dòng tiếp theo
```

Flow:
```
o = null / String / Integer  →  instanceof User? ❌  →  ! → true  →  return false
o = User object              →  instanceof User? ✅  →  ! → false →  tiếp tục so sánh id
```

---

## Tóm tắt toàn bộ `equals()`:

```java
public boolean equals(Object o) {
    if (this == o) return true;                    // 1. Cùng địa chỉ RAM → bằng nhau luôn
    if (!(o instanceof User other)) return false;  // 2. Không phải User → khác nhau luôn
    return id.equals(other.id);                    // 3. So sánh theo UUID id
}
```

| Bước | Kiểm tra | Mục đích |
|---|---|---|
| 1 | `this == o` | Short-circuit tối ưu |
| 2 | `instanceof` | Type safety + Pattern Matching |
| 3 | `id.equals()` | Business identity theo DDD |
