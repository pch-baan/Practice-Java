# ⚡ Throughput vs Latency vs Traffic

## Định nghĩa

**Throughput** = **số lượng công việc hoàn thành được trong 1 đơn vị thời gian**

```
Throughput = 1000 requests / giây
             ────────────────────
             không phải "có bao nhiêu người truy cập"
             mà là "xử lý được bao nhiêu cái xong"
```

---

## Phân biệt 3 khái niệm hay nhầm

```
Traffic (lưu lượng)      = bao nhiêu người GÕ CỬA
                           1000 người/giây gửi request

Throughput (thông lượng) = bao nhiêu người được PHỤC VỤ XONG
                           800 requests/giây xử lý thành công

Latency (độ trễ)         = mỗi người chờ BAO LÂU
                           mỗi request mất 50ms
```

---

## Tại sao Optimistic có Throughput cao hơn Pessimistic?

```
PESSIMISTIC:
Thread 1: [lock]──────────────[unlock]
Thread 2:         [chờ]───────────────[lock]──[unlock]
Thread 3:                     [chờ]──────────────────[lock]──[unlock]

→ Xếp hàng tuần tự → 1 giây xử lý được ÍT request hơn


OPTIMISTIC:
Thread 1: [đọc]──[xử lý]──[commit ✅]
Thread 2: [đọc]──[xử lý]──[commit ❌ retry]──[commit ✅]
Thread 3: [đọc]──[xử lý]──[commit ✅]

→ Không ai chờ ai → 1 giây xử lý được NHIỀU request hơn
```

---

## Tóm lại

| Khái niệm | Ý nghĩa | Đơn vị |
|---|---|---|
| **Traffic** | Bao nhiêu người gõ cửa | requests/giây |
| **Throughput** | Bao nhiêu người được phục vụ xong | requests/giây |
| **Latency** | Mỗi người chờ bao lâu | ms / giây |

> **Throughput** = thông lượng = số request xử lý xong / giây.
> Không phải "có bao nhiêu người vào" mà là "phục vụ xong được bao nhiêu người".

---

## ❓ Q&A

**Q: Traffic, Throughput, Latency thực tế được xử lý thế nào?**

A: Vấn đề cốt lõi khi traffic tăng đột biến:

```
Traffic cao  →  Throughput không đủ  →  Queue tắc  →  Latency tăng  →  User timeout

10.000 req/s đến
Server chỉ xử lý được 3.000 req/s
→ 7.000 req/s tồn đọng → chờ lâu → timeout → user thấy lỗi
```

---

**1. Load Balancer — chia traffic ra nhiều server**
```
                        ┌──► Server 1 (3.000 req/s)
10.000 req/s ──► LB ───┼──► Server 2 (3.000 req/s)
                        └──► Server 3 (4.000 req/s)

Tổng throughput: 10.000 req/s ✅
```
Scale ngang (horizontal scaling) — thêm server thay vì nâng cấp 1 server.

---

**2. Cache — chặn traffic trước khi chạm DB**
```
Request ──► Cache (Redis) ──► HIT  → trả về ngay, 1ms ✅
                          └──► MISS → xuống DB, 50ms → lưu cache
```
```
Không có cache:  10.000 req/s → DB      → DB chết
Có cache:         9.500 req/s → Cache   → DB chỉ nhận 500 req/s ✅
```
95% request trả về từ cache → DB sống, latency thấp.

---

**3. Queue — làm đệm khi traffic đột biến**
```
Bình thường:   1.000 req/s ──► Server xử lý 1.000 req/s ✅

Flash sale:   50.000 req/s ──► Queue ──► Server xử lý 3.000 req/s
                                │
                           47.000 req/s nằm chờ trong queue
                           → user thấy "Đang xử lý..."
                           → không bị lỗi, chỉ chờ lâu hơn
```
Queue làm **buffer** — traffic cao không giết server, chỉ tăng latency.

---

**4. Rate Limiting — giới hạn traffic đầu vào**
```
User A:  100 req/s  ──► OK ✅
User B:  100 req/s  ──► OK ✅
Bot  :  5.000 req/s ──► BLOCK ❌ HTTP 429 Too Many Requests
```
Chặn traffic bất thường trước khi vào hệ thống.

---

**Mối quan hệ thực tế — các lớp phòng thủ:**

```
Traffic tăng đột biến
        │
        ▼
Rate Limiter ──► chặn bot / abuse
        │
        ▼
Load Balancer ──► chia đều ra nhiều server
        │
        ▼
Cache ──► 95% request trả về ngay (latency thấp)
        │
        ▼
Queue ──► buffer phần còn lại (throughput ổn định)
        │
        ▼
DB ──► chỉ nhận lượng vừa phải (không bị quá tải)
```

---

**Bảng tổng kết:**

| Vấn đề | Giải pháp | Tác động |
|---|---|---|
| Traffic quá cao | Rate Limiting, Load Balancer | Bảo vệ server |
| DB quá tải | Cache (Redis) | Giảm 90–95% load xuống DB |
| Traffic đột biến | Queue (Kafka) | Latency tăng nhưng không lỗi |
| Throughput thấp | Scale ngang, tối ưu code | Xử lý được nhiều hơn |
| Latency cao | Cache, CDN, tối ưu query | User thấy nhanh hơn |

> Thực tế không có "1 giải pháp" — hệ thống lớn dùng **tất cả cùng lúc**,
> từng lớp giải quyết 1 vấn đề khác nhau.
