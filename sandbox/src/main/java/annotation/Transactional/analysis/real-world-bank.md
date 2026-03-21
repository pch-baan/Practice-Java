# @Transactional trong thực tế — Chuyển tiền ngân hàng

## @Transactional có đủ không?

`@Transactional` chỉ bảo vệ được **1 database duy nhất**.

```
Chuyển tiền nội bộ cùng ngân hàng:
┌─────────────────────────────┐
│        1 Database           │
│  Hùng -1M  │  Nam +1M       │  ← @Transactional đủ ✅
└─────────────────────────────┘
```

Nhưng thực tế chuyển tiền liên ngân hàng:

```
Vietcombank                    Techcombank
┌──────────────┐               ┌──────────────┐
│  DB riêng    │  ──SWIFT──>   │  DB riêng    │
│  Hùng -1M    │               │  Nam +1M     │
└──────────────┘               └──────────────┘
     @Transactional                 @Transactional
     (chỉ cover được đây)          (chỉ cover được đây)

Khoảng giữa 2 DB → @Transactional bó tay ❌
```

---

## Vậy bank thực tế dùng gì?

### 1. Saga Pattern
Mỗi bước có **compensating transaction** (hành động bù trừ) nếu thất bại:

```
Bước 1: Trừ tiền Hùng  → thành công
Bước 2: Gửi sang Techcombank → FAIL
         ↓
Compensate: Hoàn tiền lại Hùng  ← thay vì rollback, chạy lệnh ngược
```

### 2. Outbox Pattern
Ghi sự kiện vào DB cùng 1 transaction, sau đó worker gửi đi — đảm bảo không mất message.

### 3. Idempotency Key
Mỗi giao dịch có ID unique — nếu retry nhiều lần vẫn chỉ trừ tiền 1 lần.

---

## Tóm lại

| Tình huống | Dùng gì |
|---|---|
| Cùng 1 DB (cùng service) | `@Transactional` ✅ |
| Nhiều service / nhiều DB | Saga + Outbox + Idempotency |
| Liên ngân hàng (SWIFT, Napas) | Giao thức riêng của từng hệ thống |

> **`@Transactional` vẫn được dùng** trong từng service nhỏ, nhưng không phải giải pháp toàn bộ cho distributed transaction.
