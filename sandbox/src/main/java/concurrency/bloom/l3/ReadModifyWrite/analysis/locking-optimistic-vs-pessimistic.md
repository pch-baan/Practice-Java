# 🔐 DB Lock — Optimistic vs Pessimistic Locking

## Pessimistic — Lock DB ngay khi đọc

```
Thread A: SELECT ... FOR UPDATE  ← DB đặt lock ngay
              │
              │  (Thread B muốn đọc → BỊ CHẶN, phải chờ)
              │
Thread A: UPDATE ... → COMMIT → UNLOCK
                                    │
Thread B: (bây giờ mới được đọc) → SELECT ... FOR UPDATE
```

DB giữ lock **suốt thời gian** từ lúc đọc đến lúc commit.

---

## Optimistic — KHÔNG lock DB khi đọc

```
Thread A: SELECT (đọc tự do, không lock)
Thread B: SELECT (đọc tự do, không lock)  ← cùng lúc, không ai bị chặn

Thread A: UPDATE ... WHERE version=0  → version=0 còn đó → SUCCESS ✅
Thread B: UPDATE ... WHERE version=0  → version đã là 1  → 0 rows → EXCEPTION ❌
```

Không ai bị chặn lúc đọc. Chỉ **check version lúc commit** — ai commit trước thắng, người sau tự xử lý.

---

## So sánh

| | Pessimistic | Optimistic (`@Version`) |
|---|---|---|
| **Lock lúc READ?** | ✅ Có — block thread khác | ❌ Không — ai cũng đọc được |
| **Lock lúc WRITE?** | Đã có lock rồi | Check version — nếu sai thì throw |
| **Conflict nhiều?** | Phù hợp — block sớm | Chậm vì nhiều retry |
| **Conflict ít?** | Lãng phí — lock vô ích | Phù hợp — hầu hết commit được |

---

## Chọn cái nào?

| Tình huống | Dùng |
|---|---|
| Flash sale, stock thấp, nhiều người tranh | **Pessimistic** — block sớm, ít exception |
| Sửa profile, edit document, ít conflict | **Optimistic** — không lock, throughput cao |
| Không chắc | **Optimistic** trước, đo thực tế, nếu retry nhiều thì chuyển |

> Flash sale stock=1 mà 50 người mua → conflict rất nhiều → **Pessimistic** hợp lý hơn.
> `@Version` phù hợp khi conflict **ít** xảy ra (ví dụ: 2 người cùng sửa profile).

---

## ❓ Q&A

**Q: Cùng lúc 2 user gửi request thì chỉ 1 người được xử lý, người còn lại phải đợi?**

A: Không. Cả 2 đều được xử lý **ngay lập tức**, không ai phải đợi.
Vấn đề chỉ xảy ra ở **bước cuối — commit vào DB**:

```
         User A                              User B
            │                                  │
       Gửi request                        Gửi request
            │                                  │
       Đọc DB:                            Đọc DB:
       stock=1, version=0                 stock=1, version=0
            │                                  │
       stock - 1 = 0                      stock - 1 = 0
            │                                  │
       Commit: UPDATE...                  Commit: UPDATE...
       WHERE version=0                    WHERE version=0
            │                                  │
       version=0 ✅ còn đó            version đã = 1 ❌
       → Thành công                      → 0 rows updated
       → version thành 1                 → OptimisticLockingFailureException
       → Trả về: "Mua thành công"        → Trả về: "Thử lại" / HTTP 409
```

Không ai đợi ai — nhưng **người commit sau nhận exception**, app tự quyết retry hoặc báo lỗi cho user.

---

**Q: Vậy sự khác nhau về trải nghiệm user giữa 2 loại lock là gì?**

| | Optimistic | Pessimistic |
|---|---|---|
| **Lúc chờ** | Không ai chờ | User B bị block, đợi User A xong |
| **Kết quả** | 1 thành công, 1 nhận lỗi ngay | 1 thành công, 1 xử lý sau khi unblock |
| **Tốc độ** | Cả 2 nhanh, nhưng 1 thất bại | User B bị delay |

---

**Q: Optimistic có nghĩa là "lạc quan" — lạc quan cái gì?**

A: Lạc quan rằng **hầu hết thời gian sẽ không có conflict**.
Nên cứ cho mọi người đọc/xử lý thoải mái, chỉ kiểm tra lúc commit.
Nếu thực tế conflict nhiều → chiến lược này không còn "lạc quan" được nữa → đổi sang Pessimistic.

---

**Q: User bị báo lỗi xong bỏ qua (không retry) thì sao?**

A: Đó là trách nhiệm của **app, không phải DB hay lock**. Có 3 hướng xử lý:

**1. Báo lỗi — user tự retry**
```
User B nhận: HTTP 409 Conflict
             "Rất tiếc, có xung đột. Vui lòng thử lại."
             [Thử lại]
```
User thấy thông báo → bấm lại. Phù hợp khi conflict **rất hiếm**.

---

**2. Server tự retry — user không biết gì**
```java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3
)
@Transactional
public boolean purchase(Long productId) {
    // server tự thử lại tối đa 3 lần, user chỉ nhận kết quả cuối
}
```
```
User B gửi request
    → lần 1: conflict ❌  (server tự retry, user không biết)
    → lần 2: conflict ❌  (server tự retry, user không biết)
    → lần 3: hết hàng → trả về "Hết hàng"
```

---

**3. Hết hàng thật — retry vô ích**
```
stock = 1, 50 người mua
→ 1 người thành công
→ 49 người còn lại: dù retry bao nhiêu lần cũng nhận "Hết hàng"
→ Không có ích gì khi retry
```

---

**Vấn đề thật sự — phân biệt 2 loại lỗi:**

```
ObjectOptimisticLockingFailureException có 2 nguyên nhân:

1. Version conflict   → stock vẫn còn, chỉ bị người khác chen trước
                      → NÊN retry

2. Stock đã hết       → retry cũng vô ích
                      → KHÔNG retry, báo "Hết hàng"
```

Nên check lại stock sau khi bắt exception:
```java
} catch (ObjectOptimisticLockingFailureException e) {
    Product p = repo.findById(productId).orElseThrow();
    if (p.getStock() <= 0) {
        return "Hết hàng";   // retry vô ích
    }
    return "Thử lại";        // còn hàng, nên retry
}
```

> Optimistic Lock **không tự giải quyết** vấn đề cho người thua — nó chỉ **phát hiện conflict** và báo lại.
> Trách nhiệm retry hay báo lỗi thuộc về **tầng gọi nó** (Service hoặc Controller).
> Đây là lý do Pessimistic Lock đơn giản hơn cho flash sale: DB tự xếp hàng, không ai cần lo retry.

---

**Q: Thực tế các sàn ecommerce lớn cũng làm vậy không?**

A: Không. Các sàn lớn như Shopee, Lazada, Amazon **không dùng Optimistic Lock cho flash sale** — vì không đủ mạnh khi có 50.000 người bấm cùng lúc.

**1. Pre-reserve Stock — đặt cọc bằng Redis**
```
User bấm "Mua ngay"
    │
    ▼
Redis: DECR stock_available   ← atomic, cực nhanh, in-memory
    │
    ├── stock < 0 → INCR lại → trả về "Hết hàng" ngay
    │
    └── stock >= 0 → "Đặt chỗ thành công"
                         │
                     30 giây sau: thanh toán?
                         ├── Có → ghi vào DB thật
                         └── Không → INCR lại (hoàn chỗ)
```
Redis `DECR` là **atomic** — 50.000 người bấm cùng lúc, chỉ đúng số lượng thắng, không cần lock DB.

---

**2. Queue — xếp hàng chờ**
```
50.000 request ──► Message Queue (Kafka / RabbitMQ)
                          │
                  xử lý tuần tự, 1 cái 1 lúc
                          │
                  request 1   : mua được ✅
                  request 2-N : hết hàng ❌
```
Không có race condition vì chỉ có **1 consumer xử lý tại một thời điểm**.

---

**3. Pessimistic Lock — cho stock thấp, tranh chấp cao**
```sql
SELECT * FROM products WHERE id = 1 FOR UPDATE
-- block tất cả thread khác cho đến khi commit
```
Dùng khi stock ít (1–10), nhiều người tranh — đơn giản, chắc chắn.

---

**Ai dùng cái gì trong thực tế?**

| Chiến lược | Dùng khi | Ví dụ |
|---|---|---|
| **Redis DECR** | Flash sale, stock ít, traffic cực cao | Shopee 12.12, Ticketmaster |
| **Queue** | Cần thứ tự tuyệt đối, không để sót | Đặt vé máy bay, concert |
| **Pessimistic Lock** | Stock ít, hệ thống vừa, đơn giản | App thương mại nội bộ |
| **Optimistic Lock** | Conflict hiếm, không phải flash sale | Sửa profile, edit bài viết |

> `@Version` trong bài này là **công cụ học** để hiểu concept.
> Production flash sale thật sự dùng **Redis + Queue**, không ai dùng Optimistic Lock cho 50.000 người bấm cùng lúc.
