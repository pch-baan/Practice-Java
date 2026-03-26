# Fault Isolation: Async RabbitMQ vs Sync Email

## Vấn đề với Sync Email TRONG Transaction

```
┌── @Transactional ────────────────────────────────────────────┐
│  createUser()   ← DB write                                   │
│  saveToken()    ← DB write                                   │
│  sendEmail()    ← SMTP  ←── NẾU FAIL → ROLLBACK toàn bộ     │
└──────────────────────────────────────────────────────────────┘
→ Email server lỗi → ROLLBACK → user KHÔNG được tạo
→ DATA LOSS vì notification layer kéo DB layer xuống theo
```

Email server lỗi → user bị xóa. Notification layer làm hỏng DB layer.

---

## Câu chuyện minh họa: Đăng ký thành viên siêu thị

**Cách sai (sync trong transaction):**

```
Nhân viên quầy:
  1. Nhập thông tin bạn vào hệ thống ✓
  2. In thẻ thành viên ✓
  3. Gọi điện xác nhận cho bạn... 📞
        ↑ MÁY BẬN / HỎng → NHÂN VIÊN XÓA HẾT,
                             bắt bạn làm lại từ đầu 😱
```

**Cách đúng (async RabbitMQ):**

```
Nhân viên quầy:
  1. Nhập thông tin bạn vào hệ thống ✓
  2. In thẻ thành viên ✓
  3. Viết PHIẾU giao việc → bỏ vào HỘP THƯ 📬

  → XONG. Bạn đã là thành viên rồi!

  Bưu tá (worker-service):
  → Lấy phiếu từ hộp thư
  → Gọi điện cho bạn
  → Nếu thất bại → thử lại 3 lần → mới bỏ vào DLQ
```

---

## Thiết kế hiện tại: Async + AFTER_COMMIT

```
@Transactional
registerUser() {
    createUser()      → DB write
    saveToken()       → DB write
    publishEvent()    → CHỈ đặt vào hàng chờ nội bộ (Spring ApplicationEventPublisher)
}
    ↓ COMMIT thành công → DB đã lưu chắc chắn
    ↓
@TransactionalEventListener(AFTER_COMMIT)
    → BÂY GIỜ mới publish lên RabbitMQ
    → Nếu RabbitMQ lỗi ở đây → DB không bị ảnh hưởng
                                user vẫn tồn tại ✓
```

### Tại sao phải là AFTER_COMMIT, không phải BEFORE_COMMIT?

Nếu publish **trước khi commit** → RabbitMQ nhận message trong khi DB chưa chắc commit thành công.
- Worker xử lý message → query DB → user CHƯA TỒN TẠI (ghost message)
- DB sau đó rollback → message đã publish không thu hồi được

`AFTER_COMMIT` = "chắc chắn DB đã OK rồi, bây giờ mới báo cho bên ngoài".

---

## Event Flow

```
RegisterUseCaseImpl
    → publishEvent()  [Spring ApplicationEventPublisher — in-memory]
        ↓ AFTER DB COMMIT
    → UserRegisteredEventListener [@TransactionalEventListener AFTER_COMMIT]
        → RabbitMQUserRegisteredPublisher
            → Exchange: auth.exchange / Routing Key: user.registered
                → worker-service: UserRegisteredNotificationConsumer
                    → ProcessedMessageTracker.tryMarkAsProcessed() [idempotency]
                    → IWorkerEmailPort.sendVerificationEmail()
```

---

## So sánh

| Tình huống            | Sync trong transaction | Async + AFTER_COMMIT |
|-----------------------|------------------------|----------------------|
| Email server lỗi      | User bị xóa ❌         | User vẫn tồn tại ✓  |
| DB rollback           | Email không gửi ✓      | Message không publish ✓ |
| Email bị delay/retry  | Không thể retry ❌     | Retry 3 lần + DLQ ✓ |
| Fault isolation       | Không có ❌            | Hoàn toàn tách biệt ✓ |

---

## Key Design Principle

> **Tách bạch hoàn toàn giữa _lưu dữ liệu_ và _gửi thông báo_.**
>
> DB transaction chỉ chịu trách nhiệm tính toàn vẹn dữ liệu.
> Notification là side effect — nó được phép fail và retry độc lập.
