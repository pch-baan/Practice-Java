# UserJpaEntity

## Mục đích

`UserJpaEntity` là **bản đồ ánh xạ (mapping)** giữa bảng `users` trong PostgreSQL và Java object mà JPA có thể hiểu được.

Domain có `User` (pure Java, không biết DB). PostgreSQL có bảng `users`. JPA cần một class riêng để làm cầu nối — đó chính là `UserJpaEntity`.

```
PostgreSQL                 JPA                    Domain
─────────────────────────────────────────────────────────
bảng "users"    ←──→    UserJpaEntity    ←──→    User
  id (uuid)               UUID id                UUID id
  username (varchar)       String username        UsernameVO username
  email (varchar)          String email           EmailVO email
  password_hash            String passwordHash    PasswordHash passwordHash
  role (varchar)           UserRoleEnum role      UserRoleEnum role
  status (varchar)         UserStatusEnum status  UserStatusEnum status
  created_at               LocalDateTime          LocalDateTime
  updated_at               LocalDateTime          LocalDateTime
```

## Các annotation quan trọng

| Annotation | Ý nghĩa |
|---|---|
| `@Entity` | Đánh dấu class này là JPA entity — Hibernate sẽ quản lý nó |
| `@Table(name = "users")` | Ánh xạ tới bảng `users` trong DB |
| `@Id` | Đánh dấu field `id` là khóa chính |
| `@Column(updatable = false)` | Trường `id`, `created_at` không bao giờ bị UPDATE |
| `@Enumerated(EnumType.STRING)` | Lưu enum dưới dạng chuỗi ("ADMIN", "ACTIVE") thay vì số thứ tự |

## Tại sao không dùng thẳng domain `User`?

| Domain `User` | `UserJpaEntity` |
|---|---|
| Chứa Value Object (`EmailVO`, `UsernameVO`) | Chỉ chứa raw type (`String`) |
| Không có annotation JPA | Có đầy đủ `@Entity`, `@Column`, v.v. |
| Có business logic (validate, enforce rule) | Chỉ lưu trữ dữ liệu thuần túy |
| Tầng domain — không biết DB | Tầng infrastructure — biết cấu trúc bảng |

**Tách ra để domain luôn sạch, không bị ô nhiễm bởi chi tiết kỹ thuật của JPA.**
