# 4 — Production Architecture

## Bức tranh tổng thể

```
                    🌐 Internet
                         │
                         ▼
                ┌─────────────────┐
                │ ⚖️ Load Balancer │
                └─────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ 🟢 App #1    │ │ 🟢 App #2    │ │ 🟢 App #3    │
│ :8080        │ │ :8081        │ │ :8082        │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       └────────────────┼────────────────┘
                        │
                        ▼
               ┌─────────────────┐
               │ 🔀 PgBouncer    │  ← Tổng đài trung gian
               │  pool: 80       │
               └────────┬────────┘
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
┌─────────────────────┐    ┌─────────────────────┐
│ 🐘 PostgreSQL       │    │ 🐘 PostgreSQL        │
│    PRIMARY          │───►│    REPLICA           │
│  (read + write)     │    │  (read-only queries) │
└─────────────────────┘    └─────────────────────┘
```

---

## App Instance là gì?

```
📄 Code (jar file)   =   công thức làm phở

🍲 App Instance      =   1 nồi phở đang nấu theo công thức đó
```

### 1 Instance — 1 nồi phở

```
👨‍👩‍👧 Khách  ──►  🍲 Spring Boot App  ──►  🐘 PostgreSQL

         Chỉ 1 nồi → hàng dài khách chờ 😓
```

### 3 Instances (scale out) — 3 nồi phở chạy song song

```
👨‍👩‍👧 Khách  ──►  ⚖️ Load Balancer
                       │
           ┌───────────┼───────────┐
           ▼           ▼           ▼
        🍲 App #1   🍲 App #2   🍲 App #3
           │           │           │
           └───────────┼───────────┘
                       ▼
                  🐘 PostgreSQL

  3 nồi phở chạy song song → phục vụ được 3× khách 🚀
```

```bash
# Chạy 3 bản cùng lúc bằng Docker
docker-compose up --scale api-portal=3
```

---

## Vấn đề khi scale không có PgBouncer

```
Mỗi 🟢 instance giữ 20 HikariCP connections:

  1 instance  →   20 connections  →  🐘 DB  ✅ ổn
  3 instances →   60 connections  →  🐘 DB  ⚠️ nhiều rồi đấy
 10 instances →  200 connections  →  🐘 DB  🔴 DB quá tải, chậm hẳn
```

```
😤 DB phải trực 200 "đường dây nóng" liên tục
   → Dù chỉ có 8 CPU, vẫn phải context-switch 200 connections
   → Query chậm hơn dù không có thêm traffic
```

---

## 🔀 PgBouncer — Lớp quan trọng nhất

> Đây là điểm khác biệt lớn nhất giữa **amateur setup** và **production setup**.

```
  500 app connections  ──►  🔀 PgBouncer  ──►  80 real connections  ──►  🐘 DB

  DB chỉ thấy 80 connections, dù ngoài kia có bao nhiêu instance đi nữa 😎
```

```ini
# pgbouncer.ini
[databases]
practice_db = host=postgres port=5432 dbname=practice_db

[pgbouncer]
pool_mode            = transaction   # transaction pooling, không phải session
max_client_conn      = 500           # app connections vào PgBouncer
default_pool_size    = 80            # real connections ra PostgreSQL
min_pool_size        = 10
server_idle_timeout  = 600
```

**Transaction pooling** — connection chỉ giữ trong 1 transaction, trả về pool ngay sau commit/rollback.
Phù hợp REST API vì mỗi request thường chỉ cần 1 transaction ngắn.

---

## 🐘 PostgreSQL — Hardware & Config

### Hardware

| Thành phần | Minimum | Recommended |
|---|---|---|
| CPU | 4 core | **8 core** |
| RAM | 16 GB | **32 GB** |
| Storage | SSD 200 GB | **NVMe SSD 500 GB** |
| IOPS | 3000 | **8000+** |

**Tại sao NVMe?** — PostgreSQL WAL write và checkpoint flush rất nhạy cảm với disk latency.
NVMe giảm query time → throughput tăng trực tiếp theo `pool_size / query_time`.

### postgresql.conf (tuning cho 32GB RAM / 8 core)

```conf
# Memory
shared_buffers         = 8GB           # 25% RAM — hot data cache
effective_cache_size   = 24GB          # 75% RAM (hint cho query planner)
work_mem               = 64MB          # per sort/hash, cẩn thận với nhiều connections
maintenance_work_mem   = 2GB           # cho VACUUM, CREATE INDEX

# Connection — không tăng vô tội vạ, dùng PgBouncer thay vào đó
max_connections        = 200

# WAL & Checkpoint
wal_buffers            = 64MB
checkpoint_completion_target = 0.9
max_wal_size           = 4GB

# Query planner — tuning cho SSD/NVMe
random_page_cost       = 1.1           # SSD → giảm từ 4.0 xuống
effective_io_concurrency = 200         # NVMe SSD

# Autovacuum — medium traffic
autovacuum_vacuum_scale_factor   = 0.05
autovacuum_analyze_scale_factor  = 0.02
autovacuum_max_workers           = 4
```

---

## HikariCP — Mỗi App Instance

Quy tắc từ [3-hikaricp-blocked.md](3-hikaricp-blocked.md): `pool_size ≤ DB_CPU_cores × 2`

Với PostgreSQL 8 cores, PgBouncer pool 80:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20       # 3 instances × 20 = 60 → PgBouncer → 80 real connections
      minimum-idle: 5
      connection-timeout: 5000    # fail-fast: 5s
      idle-timeout: 300000        # 5 phút
      max-lifetime: 1800000       # 30 phút
      keepalive-time: 60000       # ping connection giữ sống qua PgBouncer
```

---

## Capacity Planning

| Query Time | Pool (80 real) | Throughput |
|---|---|---|
| 10ms (simple SELECT) | 80 | **8,000 req/s** |
| 50ms (JOIN 2-3 bảng) | 80 | **1,600 req/s** |
| 200ms (report query) | 80 | **400 req/s** |

**Medium company thường cần:** 200–500 req/s peak → 50ms average query time là đủ.

---

## ✅ Checklist Production

```
✅ PgBouncer transaction pooling (bắt buộc khi scale)
✅ PostgreSQL Replica cho read-heavy queries
✅ Index đúng chỗ (dùng pg_stat_user_indexes để phát hiện unused index)
✅ connection-timeout fail-fast (5s, không phải 30s mặc định)
✅ Autovacuum tuned (tránh table bloat)
✅ Monitoring: pg_stat_activity, HikariCP metrics → Grafana
✅ Backup: pgBackRest (daily full + continuous WAL archiving)
```

---

## Tóm tắt — ĐỪNG làm vs NÊN làm

```
ĐỪNG làm:                          NÊN làm:
──────────────────────────────     ──────────────────────────────────────
max_connections = 1000        →    Dùng PgBouncer transaction pooling
HikariCP pool = 100           →    pool ≤ DB_CPU_cores × 2  (= 16)
connection-timeout = 30s      →    connection-timeout = 5s (fail-fast)
1 PostgreSQL cho tất cả       →    Primary (write) + Replica (read)
```

> Nhiều connection hơn ≠ nhanh hơn.
> PostgreSQL có giới hạn concurrent query = số CPU core.
> Vượt qua đó chỉ tăng context switching overhead.

---

**Tiếp theo:** [5-cost-sizing.md](5-cost-sizing.md) — Chọn DB size theo business stage, không phải số row.
