# @Transactional — Khi nào cần dùng?

## @Transactional có phải chỉ dùng cho tiền không?

**Không.** `@Transactional` dùng cho bất kỳ thao tác nào cần **"all-or-nothing"** với database.

---

## @Transactional = ACID

| Chữ cái | Tên | Ý nghĩa |
|---------|-----|---------|
| **A** | Atomicity | All-or-nothing — hoặc tất cả thành công, hoặc rollback hết |
| **C** | Consistency | Data luôn hợp lệ sau transaction |
| **I** | Isolation | 2 transaction chạy song song không "thấy" nhau |
| **D** | Durability | Đã commit → ghi vĩnh viễn, restart không mất |

---

## Ví dụ: Chuyển tiền (liên quan money)

```java
@Transactional
public void transfer(Long fromId, Long toId, int amount) {
    accountRepo.debit(fromId, amount);   // Bước 1: trừ tiền
    accountRepo.credit(toId, amount);    // Bước 2: cộng tiền
    // Nếu exception bất kỳ → cả 2 bị ROLLBACK
}
```

---

## Ví dụ: Đăng ký user (không liên quan money)

```java
@Transactional
public void registerUser(RegisterRequest req) {
    User user = userRepo.save(newUser);         // Bước 1: tạo user
    profileRepo.save(newProfile(user.getId())); // Bước 2: tạo profile
    roleRepo.assign(user.getId(), "USER");      // Bước 3: gán role
    // Nếu Bước 2 fail mà không có @Transactional → user tồn tại nhưng không có profile → data inconsistent
}
```

---

## Quy tắc thực tế

| Tình huống | Dùng `@Transactional`? |
|---|---|
| 1 câu `SELECT` đơn giản | Không cần |
| 1 câu `INSERT`/`UPDATE` đơn lẻ | Thường không cần |
| **2+ thao tác DB phải đi cùng nhau** | **Cần** |
| Đọc rồi ghi dựa trên kết quả đọc | Cần (tránh race condition) |

---

## Câu hỏi để tự kiểm tra

> *"Nếu bước giữa chừng bị fail, data có bị lệch không?"*

- **Có** → dùng `@Transactional`
- **Không** → không cần
