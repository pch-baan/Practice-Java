# IUserRepository — Output Port

## Mục đích

`IUserRepository` là một **Output Port** trong kiến trúc Hexagonal (Ports & Adapters).

Interface này định nghĩa **những gì domain cần từ tầng lưu trữ**, mà không quan tâm đến chi tiết kỹ thuật (PostgreSQL, JPA, v.v.).

```
Tầng Application (UseCase)
        │
        │  sử dụng
        ▼
IUserRepository  ← Output Port (domain tự định nghĩa)
        ▲
        │  implement
        │
UserPostgresqlAdapter  ← Infrastructure (JPA + PostgreSQL)
```

## Các method

| Method | Mục đích |
|---|---|
| `save(User)` | Tạo mới hoặc cập nhật user |
| `findById(UUID)` | Tìm user theo ID |
| `findByUsername(UsernameVO)` | Tìm user khi đăng nhập bằng username |
| `findByEmail(EmailVO)` | Tìm user khi đăng nhập bằng email |
| `existsByEmail(EmailVO)` | Kiểm tra email đã tồn tại chưa (dùng khi đăng ký) |
| `existsByUsername(UsernameVO)` | Kiểm tra username đã tồn tại chưa (dùng khi đăng ký) |

## Tại sao cần interface này?

**Nguyên tắc Dependency Inversion** — tầng domain KHÔNG được phụ thuộc vào JPA hay PostgreSQL.

- Nếu không có interface: `UserUseCase` phải import thẳng `UserJpaRepository` → domain bị ràng buộc với infrastructure.
- Với interface: `UserUseCase` chỉ biết đến `IUserRepository` → có thể thay thế sang MongoDB, InMemory, hoặc mock khi viết test mà không cần sửa business logic.

**Domain ra lệnh. Infrastructure tuân theo.** Interface này là "hợp đồng" giữa hai bên.
