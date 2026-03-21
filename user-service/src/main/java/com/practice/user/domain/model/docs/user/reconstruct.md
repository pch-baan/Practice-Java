# User.reconstruct() — Tái tạo User từ Database

## Mục đích

`reconstruct()` là static factory method dùng để **phục hồi một User đã tồn tại trong DB** lên thành Domain Model.
Nó KHÔNG tạo user mới — chỉ ánh xạ dữ liệu từ persistence layer về domain layer.

## So sánh với create()

| | `create()` | `reconstruct()` |
|---|---|---|
| Khi nào dùng | Đăng ký user mới | Load user từ DB |
| UUID | Tự sinh (`UUID.randomUUID()`) | Dùng ID đã có trong DB |
| Role / Status | Mặc định (`USER`, `ACTIVE`) | Lấy đúng giá trị từ DB |
| `createdAt` | `LocalDateTime.now()` | Thời gian thật từ DB |

## Ai gọi reconstruct()?

Tầng `infrastructure` — cụ thể là JPA mapper/adapter — gọi `reconstruct()` khi load User từ DB:

```
Database
   │
   ▼
UserJpaEntity  (infrastructure layer)
   │  mapper đọc dữ liệu từ DB
   ▼
User.reconstruct(...)   ← gọi ở đây
   │
   ▼
Domain Model User  (sẵn sàng dùng trong business logic)
```

## Tại sao reconstruct() là cần thiết?

Constructor của `User` là `private` → bên ngoài không thể gọi `new User(...)`.

Nếu không có `reconstruct()`, chỉ còn 2 lựa chọn — cả 2 đều sai:

```java
// ❌ Dùng create() — SAI vì sinh UUID mới, reset createdAt
User user = User.create(entity.getUsername(), ...);
//           ↑ ID khác với DB, mất toàn bộ dữ liệu gốc!

// ❌ Dùng reflection để bypass private constructor — SAI, phá vỡ encapsulation
Constructor<User> c = User.class.getDeclaredConstructor(...);
c.setAccessible(true); // anti-pattern
```

`reconstruct()` là **cánh cửa có kiểm soát** dành riêng cho infrastructure —
thay vì phá tường (reflection) hoặc dùng sai cửa (`create()`).

## Ví dụ sử dụng đúng

```java
// Trong UserPersistenceMapper (infrastructure layer)
public User toModel(UserJpaEntity entity) {
    return User.reconstruct(
        entity.getId(),
        entity.getUsername(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getRole(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
}
```
