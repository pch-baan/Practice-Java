# @Override và @Transactional

## @Override

Java annotation thuần túy — nói với **compiler**: _"Method này đang override method từ interface/superclass."_

```java
public interface ICreateUserUseCase {
    UserResponseDto execute(CreateUserCommandDto command); // ← định nghĩa ở đây
}

public class CreateUserUseCaseImpl implements ICreateUserUseCase {

    @Override  // ← "tôi đang implement method trên"
    public UserResponseDto execute(CreateUserCommandDto command) { ... }
}
```

**Tại sao cần?** Không có `@Override` vẫn chạy được. Nhưng nếu gõ sai tên method:

```java
// Không có @Override → compiler im lặng, vô tình tạo method mới
public UserResponseDto excute(...) { ... }  // typo, không ai báo lỗi

// Có @Override → compiler báo lỗi ngay
@Override
public UserResponseDto excute(...) { ... }  // ❌ compile error: method không tồn tại trong interface
```

---

## @Transactional

Nói với Spring: _"Bọc toàn bộ method này trong 1 database transaction."_

```
BEGIN TRANSACTION
    ↓
EmailVO.of(...)                      — validate
UsernameVO.of(...)                   — validate
userDomainService.validate(...)      — query DB kiểm tra trùng
passwordEncoder.encode(...)          — hash password
User.create(...)                     — tạo entity
userRepository.save(user)            — INSERT vào DB
    ↓
COMMIT  ← nếu không có exception
   hoặc
ROLLBACK ← nếu có exception
```

**Nếu không có `@Transactional`**, mỗi bước là 1 connection DB riêng biệt:

```
// Kịch bản nguy hiểm khi không có @Transactional:
userDomainService.validate(email, username)  ← DB query 1: email chưa tồn tại ✅
// ... thread khác insert cùng email vào đây ...
userRepository.save(user)                    ← DB query 2: INSERT → duplicate key ❌
```

Với `@Transactional`, cả 2 operation nằm trong cùng 1 transaction → **atomic** (toàn bộ thành công hoặc toàn bộ rollback).

---

## Tóm tắt

| Annotation | Của ai | Tác dụng |
|-----------|--------|---------|
| `@Override` | Java compiler | Bảo vệ tại compile-time: đảm bảo đang override đúng method |
| `@Transactional` | Spring | Bọc method trong DB transaction, tự động commit/rollback |
