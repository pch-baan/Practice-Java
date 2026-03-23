# Phân tích Connection Pool Exhaustion — BCrypt trong @Transactional

## Thông số server

```
PostgreSQL server: 4 vCPU, 8GB RAM
App config:
  maximum-pool-size  = 10  (tính theo công thức bên dưới)
  connection-timeout = 5,000ms
```

---

## Bước 1 — Tính recommended pool size

Công thức chuẩn từ HikariCP + PostgreSQL wiki:

```
pool_size = (số vCPU × 2) + số ổ đĩa
          = (4 × 2) + 1        ← SSD tính là 1
          = 9  →  làm tròn 10
```

**Recommended `maximum-pool-size = 10`**

---

## Bước 2 — Thời gian 1 request giữ connection

Với code hiện tại (BCrypt **trong** `@Transactional`):

```
existsByEmail()      ≈   5ms
existsByUsername()   ≈   5ms
BCrypt encode()      ≈ 300ms  ← thủ phạm
save(user)           ≈  10ms
save(profile)        ≈  10ms
─────────────────────────────
Tổng hold time       ≈ 330ms
```

Sau khi fix (BCrypt **ngoài** `@Transactional`):

```
existsByEmail()      ≈   5ms
existsByUsername()   ≈   5ms
save(user)           ≈  10ms
save(profile)        ≈  10ms
─────────────────────────────
Tổng hold time       ≈  30ms
```

---

## Bước 3 — Tính ngưỡng gây lỗi

### Max throughput (sustained load)

```
pool_size / hold_time = 10 / 0.33s ≈ 30 request/giây   (có bug)
pool_size / hold_time = 10 / 0.03s ≈ 333 request/giây  (sau fix)
```

### Max burst (nhiều request ập đến cùng 1 lúc)

```
Trong timeout = 5000ms, pool xử lý được bao nhiêu batch?

  Có bug:   floor(5000 / 330) = 15 batches × 10 = 150 request
  Sau fix:  floor(5000 /  30) = 166 batches × 10 = 1,660 request

Max burst an toàn = pool_size + số request xử lý được trong timeout

  Có bug:   10 + 150  =   160 request đồng thời
  Sau fix:  10 + 1660 = 1,670 request đồng thời
```

---

## Bảng tổng kết

```
                    │  Có bug              │  Sau fix
                    │  (BCrypt trong tx)   │  (BCrypt ngoài tx)
────────────────────┼──────────────────────┼──────────────────────
Hold time           │  330ms               │  30ms
────────────────────┼──────────────────────┼──────────────────────
Max throughput      │  ~30 req/s           │  ~333 req/s
────────────────────┼──────────────────────┼──────────────────────
Max burst an toàn   │  ~160 request        │  ~1,670 request
────────────────────┼──────────────────────┼──────────────────────
Hệ số cải thiện     │  1x                  │  10x
```

---

## Ý nghĩa thực tế

| Tình huống | Ngưỡng gây lỗi (có bug) |
|---|---|
| **Sustained load** | > 30 request/giây liên tục |
| **Burst** (cùng 1 lúc) | > 160 request đồng thời |

Ví dụ: campaign marketing gửi email cho 10,000 người.
Chỉ cần **1.6%** (160 người) click đăng ký trong cùng 1 giây → lỗi bắt đầu xuất hiện.

---

## Lưu ý

- BCrypt 300ms là ước tính trung bình trên 4 vCPU. Thực tế dao động 200-400ms.
- Các con số trên giả định **1 app instance**. Nếu có nhiều instance,
  tổng connections = `pool_size × số instance` không được vượt quá
  `max_connections` của PostgreSQL server.
- Công thức burst chỉ đúng khi tất cả request đến **đúng cùng 1 lúc**.
  Thực tế traffic phân bổ theo Poisson distribution → ngưỡng thực tế thấp hơn một chút.
