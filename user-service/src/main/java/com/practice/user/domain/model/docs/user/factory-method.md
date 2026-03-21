# Static Factory Method — `User.create()`

## Đây là Static Factory Method, không phải constructor!

### So sánh trực quan

```
Constructor thông thường:          Static Factory Method:
──────────────────────────         ──────────────────────────────────
new User(id, username, ...)        User.create(username, email, hash)
     ▲                                  ▲
 gọi trực tiếp                    gọi qua method tĩnh
 không có tên                     CÓ tên rõ ràng: "create"
 luôn tạo object mới              có thể kiểm soát logic trước khi tạo
```

---

### Storytelling

> **Constructor** = bạn tự in thẻ tại nhà — muốn gì in nấy, không ai kiểm tra.
>
> **Factory Method** = bạn ra ngân hàng xin mở thẻ — nhân viên kiểm tra đủ điều kiện,
> tự điền ngày tạo, tự gán loại thẻ mặc định, rồi mới phát thẻ cho bạn.

---

### `create()` làm 3 việc mà constructor không tự làm được

```java
public static User create(String username, String email, String passwordHash) {
    return new User(
        UUID.randomUUID(),       // tự sinh ID, caller không cần biết
        UsernameVO.of(username), // validate + wrap vào Value Object
        EmailVO.of(email),       // validate + wrap vào Value Object
        PasswordHashVO.of(...),  // validate + wrap vào Value Object
        UserRoleEnum.USER,       // mặc định role = USER, không cho caller tự chọn
        UserStatusEnum.ACTIVE,   // mặc định status = ACTIVE
        LocalDateTime.now(),     // tự set thời gian, caller không cần truyền
        LocalDateTime.now()
    );
}
```

---

### Tại sao constructor lại `private`?

```java
private User(UUID id, UsernameVO username, ...) { ... }
//  ▲
//  private → bên ngoài class KHÔNG THỂ gọi "new User(...)"
//  Buộc mọi người phải đi qua create() hoặc reconstruct()
```

---

### Tóm tắt

| | Constructor `new User()` | Factory Method `User.create()` |
|---|---|---|
| Ai gọi được? | Bị chặn (`private`) | Ai cũng gọi được |
| Tên | Không có | Có tên rõ: `create` vs `reconstruct` |
| Kiểm soát logic | Không | Có (default role, validate VO, auto ID) |
| Dùng khi | Nội bộ class | Tạo User mới từ bên ngoài |
