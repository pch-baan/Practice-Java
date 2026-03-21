# 3 — HikariCP & BLOCKED State

## Bottleneck nằm ở đâu?

```
Request đi qua 2 giới hạn:

  Tomcat Thread Pool  (default 200 threads)
         │
         ▼
  HikariCP Pool       (default 10 connections)  ← Bottleneck thực sự
         │
         ▼
       Database
```

200 Tomcat threads nhận request, nhưng chỉ **10** thread được query DB cùng lúc.
190 thread còn lại → **BLOCKED** chờ connection.

---

## Công thức Throughput

```
Throughput = pool_size / query_time
```

```
Pool = 10 connection

Query mất 100ms:   10 / 0.1s  = 100 req/s  ✅
Query mất 500ms:   10 / 0.5s  =  20 req/s  ⚠️
Query mất 2000ms:  10 / 2.0s  =   5 req/s  🔴
```

### Hình dung bằng băng chuyền

```
Pool = 10 làn băng chuyền

Query 100ms  → mỗi làn xử lý 10 đơn/giây  → 10 làn = 100 đơn/giây
Query 500ms  → mỗi làn xử lý  2 đơn/giây  → 10 làn =  20 đơn/giây
Query 2000ms → mỗi làn xử lý 0.5 đơn/giây → 10 làn =   5 đơn/giây
```

**Kết luận:** Tối ưu query 500ms → 100ms = throughput tăng **5×**, không tốn thêm 1 đồng.

---

## connection-timeout làm gì?

Nó **không tăng throughput** — chỉ quyết định request chờ bao lâu trước khi bị HTTP 500:

```
100 user đến cùng lúc, throughput = 20 req/s

  10 user   → xử lý ngay (có connection)
  90 user   → xếp hàng chờ

  Thời gian xử lý hết hàng = 90/20 = 4.5 giây

  connection-timeout = 30s → tất cả 90 user chờ được  ✅
  connection-timeout = 3s  → user chờ > 3s → HTTP 500  💥
```

**Production nên đặt 5s (fail-fast)** — client biết lỗi sớm, tránh queue tích lũy dài.

---

## Demo BLOCKED — Pool bị cạn kiệt

### Cấu hình demo

```
Pool size     = 3 slot
Query time    = 1000ms  (giả lập DB chậm)
Timeout       = 600ms   (hikari.connection-timeout)
```

---

### Wave 1 — 6 request đến cùng lúc (t=0ms)

```
t=0ms   6 request đến CÙNG LÚC
        │
        ▼
┌──────────────────────────────────────────────┐
│          CONNECTION POOL (3 slot)            │
│  [ slot 1 ]   [ slot 2 ]   [ slot 3 ]        │
└──────────────────────────────────────────────┘

R1, R2, R3 vào được ngay ✅
┌──────────────────────────────────────────────┐
│  [ R1 🔵 ]    [ R2 🔵 ]    [ R3 🔵 ]        │  ← POOL ĐẦY
└──────────────────────────────────────────────┘
        🔴 R4       🔴 R5       🔴 R6
        │           │           │
        └───────────┴───────────┘
          ⏳ BLOCKED — xếp hàng chờ tối đa 600ms
```

```
t=600ms   R4, R5, R6 chờ quá 600ms — pool vẫn đầy
┌──────────────────────────────────────────────┐
│  [ R1 🔵 ]    [ R2 🔵 ]    [ R3 🔵 ]        │  ← vẫn đang query
└──────────────────────────────────────────────┘

💥 R4 → ConnectionTimeoutException → 🚨 HTTP 500
💥 R5 → ConnectionTimeoutException → 🚨 HTTP 500
💥 R6 → ConnectionTimeoutException → 🚨 HTTP 500
```

```
t=1000ms   R1, R2, R3 query xong → trả slot về
┌──────────────────────────────────────────────┐
│  [ trống ]    [ trống ]    [ trống ]          │  ← pool trống
└──────────────────────────────────────────────┘
  (nhưng R4, R5, R6 đã timeout rồi — không ai nhận slot này nữa)
```

---

### Wave 2 — 3 request mới đến lúc pool vừa trống (t=1100ms)

```
t=1100ms   R7, R8, R9 đến — pool trống hoàn toàn
┌──────────────────────────────────────────────┐
│  [ R7 🔵 ]    [ R8 🔵 ]    [ R9 🔵 ]        │
└──────────────────────────────────────────────┘

✅ R7, R8, R9 vào được ngay — không phải chờ
```

---

### Timeline tổng quát

```
t=0ms      │ R1, R2, R3 ──► [query DB 1000ms]
           │ R4, R5, R6 ──► ⏳ BLOCKED chờ...
           │
t=600ms    │ R4, R5, R6 ──► 💥 TIMEOUT ──► 🚨 HTTP 500
           │
t=1000ms   │ R1, R2, R3 ──► 🔓 trả connection
           │
t=1100ms   │ R7, R8, R9 ──► ✅ vào được ngay ──► [query DB]
           │
t=2100ms   │ R7, R8, R9 ──► 🔓 trả connection ──► DONE
```

---

## Nhà hàng analogy

```
Pool = 3 chỗ ngồi trong nhà hàng

6 khách đến cùng lúc:
  → 3 khách đầu: có chỗ, ngồi vào ăn ngay  ✅
  → 3 khách sau: đứng chờ tối đa 600ms

Nếu 600ms mà 3 khách đầu chưa ăn xong:
  → 3 khách sau: nhà hàng báo "hết chỗ"    🚨 HTTP 500
```

**Không phải lỗi của user** — server quá tải, pool không đủ chỗ.

---

## Log lỗi thực tế

Khi thấy log này:
```
Unable to acquire JDBC Connection
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

Có 2 hướng fix:

| Hướng fix | Cách làm |
|---|---|
| **Tối ưu query** | Query nhanh hơn → trả connection sớm hơn → pool không bị giữ lâu |
| **Tăng pool** | `maximum-pool-size: 20` (thêm chỗ ngồi) — nhưng phải kiểm tra DB server chịu được không |

---

## Mapping thực tế Spring Boot

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # POOL_SIZE
      connection-timeout: 5000  # 5s, fail-fast (mặc định 30s — quá dài)
      minimum-idle: 5
```

| Demo | Spring Boot thực tế |
|---|---|
| `Semaphore(3)` | HikariCP pool 10 connection |
| `tryAcquire(600ms)` | `hikari.connection-timeout=5000` |
| `💥 TIMEOUT + return` | `ConnectionTimeoutException` → HTTP 500 |
| Wave 2 vào được | Request đến sau khi traffic giảm — pool có slot trống |

---

## Quy tắc vàng

```
pool_size ≤ DB_CPU_cores × 2
```

Tăng pool vô hạn không giúp ích — PostgreSQL xử lý concurrent query tối đa bằng số CPU core.
Vượt qua đó → DB context-switch liên tục → query chậm hơn dù pool lớn hơn.

---

**Tiếp theo:** [4-production-architecture.md](4-production-architecture.md) — Scale nhiều instance, PgBouncer, PostgreSQL production config.
