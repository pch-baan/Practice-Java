# Flow Register — Đủ dùng trên Production chưa?

Trả lời ngắn: **Chưa đủ.**
Flow đúng về kiến trúc và logic, nhưng thiếu một số thứ bắt buộc trước khi production.

---

## Critical — Phải có trước khi production

### 1. Không có email verification

```
Hiện tại:
  POST /register → User.status = ACTIVE ngay lập tức → nhận token

Vấn đề:
  - Ai cũng có thể đăng ký bằng email người khác
  - Bot có thể tạo hàng nghìn tài khoản rác

Production phải có:
  POST /register → User.status = PENDING
                 → gửi email chứa verification token
  GET /verify?token=xxx → User.status = ACTIVE → nhận JWT
```

### 2. Không có rate limiting

```
Hiện tại: không giới hạn số lần gọi /register

Vấn đề:
  - 1 IP có thể spam POST /register liên tục
  - BCrypt ~300ms × 1000 req = server bị đánh bại bằng CPU

Cần thêm:
  - Spring Rate Limiter hoặc API Gateway throttling
  - VD: tối đa 5 lần đăng ký/IP/phút
```

---

## Important — Nên có

### 3. Password strength policy quá yếu

```
Hiện tại:
  @Size(min = 8)  ← chỉ kiểm tra độ dài

Password "aaaaaaaa" hợp lệ.

Nên thêm:
  - Ít nhất 1 chữ hoa
  - Ít nhất 1 số
  - Ít nhất 1 ký tự đặc biệt
  - Không trùng với username
```

### 4. Không có audit log

```
Hiện tại: đăng ký thành công/thất bại → không ghi lại gì

Production cần:
  log.info("USER_REGISTERED userId={} ip={} at={}", userId, clientIp, now)
  log.warn("REGISTER_FAILED reason=DUPLICATE email={} ip={}", email, clientIp)

→ phát hiện tấn công, điều tra sự cố
```

### 5. Username/email enumeration

```
Hiện tại:
  POST /register với email đã tồn tại → 409 Conflict "Email already exists"

Vấn đề:
  Attacker biết chính xác email nào đã đăng ký trong hệ thống.

Tuỳ ngữ cảnh — với nhiều app điều này chấp nhận được vì UX,
nhưng hệ thống nhạy cảm (banking, healthcare) cần che đi.
```

---

## Kỹ thuật — Câu hỏi về transaction

Flow hiện tại thực ra **an toàn** trong modular monolith:

```
RegisterUseCaseImpl @Transactional → mở TX-1
  │
  └─ CreateUserUseCaseImplV3
       TransactionTemplate (PROPAGATION_REQUIRED)
       → tham gia TX-1 (không mở TX mới)
       → INSERT users + user_profiles
  │
  └─ refreshTokenRepository.save()
       → cũng trong TX-1
       → INSERT refresh_tokens
  │
TX-1 commit (tất cả hoặc không)
```

Nếu chuyển sang **microservices thật** (user-service DB riêng, auth-service DB riêng)
→ đây là **distributed transaction** → cần Saga pattern.
Hiện tại OK vì chung 1 DB.

---

## Tóm tắt

| Vấn đề | Mức độ | Có không? |
|--------|--------|-----------|
| Email verification | Critical | Không |
| Rate limiting | Critical | Không |
| Password strength | Important | Yếu |
| Audit logging | Important | Không |
| Transaction safety | OK | Có (monolith) |
| Duplicate error handling | OK | Có (409) |
| Input validation (@Valid) | OK | Có |
