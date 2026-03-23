# Ghi chú: Tại sao đặt Email Adapter trong `infrastructure/email/`?

## Q1: Đặt adapter trong `infrastructure/email/` có hợp lý không?

**Trả lời: Hoàn toàn hợp lý.** Đây là pattern chuẩn của Hexagonal Architecture.

```
application/port/out/IEmailPort.java            ← Interface (boundary)
         ↑ implements
infrastructure/email/JavaMailSenderAdapter.java  ← Kỹ thuật cụ thể (JavaMail)
infrastructure/email/NoOpEmailAdapter.java       ← Fallback dev/test
```

`infrastructure/` chứa mọi thứ kỹ thuật cụ thể — code phụ thuộc vào thư viện bên ngoài hoặc I/O:

| Folder | Phụ thuộc vào |
|---|---|
| `infrastructure/persistence/` | PostgreSQL, JPA |
| `infrastructure/security/` | JJWT |
| `infrastructure/external/` | user-service |
| `infrastructure/email/` | Spring `JavaMailSender` |

Application layer chỉ biết đến `IEmailPort`, không biết gì về `JavaMailSender`.

---

## Q2: Tại sao không đặt trong `infrastructure/external/`?

Sự khác biệt nằm ở **loại phụ thuộc**:

```
infrastructure/external/  → gọi service KHÁC trong cùng hệ thống
infrastructure/email/     → gọi hạ tầng bên ngoài (SMTP server)
```

**`external/`** — dependency là **business service** khác:

```
ActivateUserServiceAdapter → IActivateUserUseCase  (user-service)
CreateUserServiceAdapter   → ICreateUserUseCase    (user-service)
```
→ Đây là service-to-service call, phụ thuộc vào domain khác.

**`email/`** — dependency là **kỹ thuật hạ tầng** (không phải domain):

```
JavaMailSenderAdapter → JavaMailSender  (Spring Mail / SMTP)
```
→ Giống như database hay JWT — không có business logic nào ở đây.

**Tiêu chí phân loại:**

| Câu hỏi | Folder |
|---|---|
| Đang gọi một **service/domain** khác? | `external/` |
| Đang gọi một **công nghệ** (DB, mail, cache...)? | folder riêng theo tên công nghệ |

---

## Q3: Trong tương lai có nhiều hạ tầng khác thì tổ chức thế nào?

Folder `infrastructure/` mở rộng tự nhiên theo từng concern:

```
infrastructure/
├── config/
├── email/           ← SMTP, SendGrid, SES
├── sms/             ← Twilio, SNS
├── cache/           ← Redis
├── messaging/       ← Kafka, RabbitMQ
├── external/        ← service-to-service calls
├── persistence/     ← PostgreSQL, JPA
└── security/        ← JWT
```

Mỗi folder = **1 công nghệ / 1 concern**. Bên trong mỗi folder vẫn giữ pattern adapter:

```
email/
├── JavaMailSenderAdapter.java   ← production
└── NoOpEmailAdapter.java        ← dev/test
```

**Quy tắc:** `1 công nghệ = 1 folder trong infrastructure/`

Không có giới hạn số lượng folder — miễn là mỗi folder có tên rõ ràng và adapter bên trong implement port từ `application/port/out/`.
