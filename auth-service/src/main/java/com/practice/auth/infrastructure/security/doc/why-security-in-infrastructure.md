# Tại sao `security/` nằm trong `infrastructure/`?

## Infrastructure có nhiều loại kỹ thuật khác nhau

```
infrastructure/
├── persistence/     ← kỹ thuật LƯU TRỮ (DB)
├── external/        ← kỹ thuật GỌI SERVICE KHÁC
├── config/          ← kỹ thuật CẤU HÌNH Spring
└── security/        ← kỹ thuật BẢO MẬT (JWT, crypto)
```

Mỗi subfolder đại diện cho một **technical concern** (mối quan tâm kỹ thuật),
không phải business concern.

---

## Tại sao JwtServiceImpl không thuộc các folder khác?

| Folder | Câu hỏi | Trả lời |
|---|---|---|
| `persistence/` | JWT có lưu xuống DB không? | Không — generate in-memory |
| `external/` | JWT có gọi sang service khác không? | Không — jjwt chạy locally |
| `config/` | JWT có phải là Spring `@Configuration` không? | Không — là `@Component` thực thi logic |

→ Chỉ còn `security/` là phù hợp.

---

## Tại sao gọi là `security/`?

`JwtServiceImpl` implements `IJwtPort` — một **cơ chế bảo mật** (authentication token).
Nó dùng:
- Cryptographic signing key (HMAC-SHA256)
- Secret key từ `JwtProperties`
- jjwt library để ký và tạo token

Đây là **technical detail của security**, không phải persistence hay external call.

---

## Nếu sau này có thêm?

```
infrastructure/
└── security/
    ├── JwtServiceImpl.java        ← JWT generation/validation
    ├── TokenBlacklistAdapter.java ← revoked tokens (nếu dùng Redis)
    └── PasswordHashAdapter.java   ← nếu tách BCrypt ra khỏi Spring
```

Tất cả đều là "kỹ thuật bảo mật" — gom vào `security/` giúp navigate nhanh hơn
so với để lẫn lộn với persistence hay config.

---

## Tóm lại

> `infrastructure/security/` không phải là Spring Security config —
> đó là nơi chứa **các implementation kỹ thuật liên quan đến bảo mật**
> mà domain/application layer không cần biết chi tiết.
>
> Giống như `persistence/` chứa JPA details,
> `security/` chứa crypto/token details.
