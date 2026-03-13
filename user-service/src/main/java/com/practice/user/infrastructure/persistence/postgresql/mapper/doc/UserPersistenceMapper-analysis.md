# UserPersistenceMapper — Flow Analysis

## Overview

`UserPersistenceMapper` là cầu nối giữa **Domain Layer** và **Infrastructure Layer**.
Nó chuyển đổi dữ liệu theo hai chiều: Domain ↔ JPA Entity.

---

## Full Flow: CreateUser

```
CreateUserUseCaseImpl.execute()
│
├─ User user = User.create(username, email, passwordHash)
│   └─ new User() trong memory — CHƯA có trong DB
│      id = UUID.randomUUID()  ← tự sinh
│      createdAt = NOW()        ← tự set
│
├─ userRepository.save(user)
│
│   [Bên trong UserPostgresqlAdapter.save()]
│   │
│   ├─ UserJpaEntity entity = mapper.toJpaEntity(user)
│   │   └─ Domain User → JPA Entity (để Hibernate hiểu được)
│   │
│   ├─ UserJpaEntity savedEntity = jpaRepository.save(entity)
│   │   └─ Hibernate INSERT vào PostgreSQL
│   │      DB có thể tự set: updatedAt, trigger...
│   │      savedEntity = object phản ánh những gì đang trong DB
│   │
│   └─ return mapper.toDomain(savedEntity)
│       └─ User.reconstruct(savedEntity.getId(), ...)
│          └─ new User() từ DATA THỰC TẾ TRONG DB
│
└─ savedUser = kết quả trả về
    └─ Domain User phản ánh đúng trạng thái DB
```

---

## Hai Factory Methods: create() vs reconstruct()

| | `User.create()` | `User.reconstruct()` |
|---|---|---|
| **Dùng khi** | Tạo user mới (register) | Load lại từ DB |
| **UUID** | Tự sinh mới | Dùng UUID đã có trong DB |
| **createdAt** | `LocalDateTime.now()` | Lấy từ DB (không đổi) |
| **role/status** | Set mặc định (USER/ACTIVE) | Dùng giá trị thực trong DB |
| **Validation** | Có (throw exception nếu sai) | Không (data đã clean) |
| **Ý nghĩa** | "Khai sinh" | "Đọc hồ sơ từ tủ lưu trữ" |

---

## Tại sao phải reconstruct() sau khi save?

Vì `user` (trước save) và `savedEntity` (sau save) **có thể khác nhau**:

| Field | `user` trước save | `savedEntity` sau save |
|---|---|---|
| `id` | UUID tự sinh trong code | ID được confirm từ DB |
| `updatedAt` | Set bởi code | Có thể bị DB override (trigger) |
| `version` | Chưa có | Hibernate đặt nếu dùng `@Version` |

Nếu dùng `user` (trước save) để trả về response → data có thể **không khớp với DB**.

`savedUser` = bản sao chính xác từ DB sau khi đã lưu xong.

---

## Mapper Methods

### `toJpaEntity(User user)` — Domain → JPA
- Chuyển Domain User sang UserJpaEntity để Hibernate lưu được
- Extract các primitive values từ ValueObjects (UsernameVO, EmailVO, PasswordHashVO)

### `toDomain(UserJpaEntity entity)` — JPA → Domain
- Gọi `User.reconstruct(...)` với toàn bộ data từ DB
- Tạo lại Domain User phản ánh trạng thái thực tế trong DB

---

## Private Constructor Pattern

```java
// Constructor private — không ai gọi trực tiếp được từ bên ngoài
private User(...) { ... }

// Chỉ có 2 cửa vào hợp lệ:
public static User create(...)       // Cửa "sinh ra"
public static User reconstruct(...)  // Cửa "hồi sinh từ DB"
```

Mục đích: **kiểm soát hoàn toàn** cách một User được tạo ra, tránh tạo object ở trạng thái không hợp lệ.
