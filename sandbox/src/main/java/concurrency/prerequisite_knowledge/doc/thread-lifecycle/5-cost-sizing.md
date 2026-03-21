# 5 — Cost & Sizing

## ⚠️ Hiểu đúng trước khi sizing

```
❌ Hiểu sai:
   50k rows trong bảng users  →  cần DB lớn

✅ Hiểu đúng:
   50k Monthly Active Users (MAU)
          │
          ▼
      ~5k DAU
          │
          ▼
   ~200–500 concurrent requests lúc peak
          │
          ▼
   ĐÂY mới là thứ ảnh hưởng DB sizing
```

---

## Cái thực sự ảnh hưởng DB sizing

```
  ┌──────────────────────────────────────────────────────┐
  │  1. Concurrent connections  ← bao nhiêu req cùng lúc? │
  │  2. Query complexity        ← JOIN mấy bảng? index?    │
  │  3. Write throughput        ← INSERT/UPDATE bao nhiêu? │
  │  4. Hot data fits in RAM?   ← shared_buffers đủ không? │
  └──────────────────────────────────────────────────────┘
```

```
   1 triệu rows   →  ✅ Không đáng kể   (index đúng = < 1ms)
 100 triệu rows   →  ⚠️ Cần index tốt, query plan
   1 tỷ rows      →  🔴 Cần partition table
```

> `SELECT * WHERE id = ?` trên 50k rows với index → **< 1ms** dù DB chỉ $15/tháng.

---

## 💰 DigitalOcean — PostgreSQL Pricing (2026)

### Option A: Managed PostgreSQL

> DigitalOcean tự lo: backup, failover, update, monitor

```
💲 Pricing ladder:

  $15/mo  ██░░░░░░░░  DB-Basic   1 shared vCPU |  1 GB RAM |  10 GB  → Dev/test
  $60/mo  ████░░░░░░  DB-Large   2 vCPU        |  4 GB RAM |  60 GB  → Small app
 $120/mo  █████░░░░░  DB-XL      4 vCPU        |  8 GB RAM | 140 GB  → ⚠️ Minimum
 $240/mo  ███████░░░  DB-2XL     6 vCPU        | 16 GB RAM | 290 GB  → ✅ Medium
 $480/mo  ██████████  DB-3XL     8 vCPU        | 32 GB RAM | 580 GB  → ✅ Recommended
```

> 💾 Extra storage: **+$0.21/GB/tháng**
> 🔁 High Availability (Primary + 1 Standby) = **nhân đôi giá**
> → DB-2XL HA = **$480/tháng**

---

### Option B: Self-managed trên Droplet

> Tự cài PostgreSQL + PgBouncer, tự quản lý

```
General Purpose  4 vCPU | 16 GB |  100 GB SSD  →  ~$96/mo
General Purpose  8 vCPU | 32 GB |  200 GB SSD  → ~$192/mo  ✅ match recommendation
CPU-Optimized    4 vCPU |  8 GB |   25 GB NVMe →   $84/mo
CPU-Optimized    8 vCPU | 16 GB |   50 GB NVMe → ~$168/mo
Memory-Optimized 4 vCPU | 32 GB |  100 GB SSD  → ~$168/mo
```

---

### ⚖️ Managed vs Self-managed

```
              MANAGED 🤝                    SELF-MANAGED 🔧
         ─────────────────────           ─────────────────────
  Setup  5 phút click-click         vs   Tự cài PostgreSQL, PgBouncer
     HA  Tự động failover           vs   Tự cấu hình Patroni / repmgr
 Backup  Auto PITR 7 ngày           vs   Tự cấu hình pgBackRest
Monitor  Dashboard sẵn              vs   Tự cài Prometheus + Grafana
   Giá  Cao hơn 2–2.5x             vs   Rẻ hơn
   For   Team nhỏ, không có DBA     vs   Có senior DevOps / DBA
```

---

## 🌍 So sánh cloud (cùng 8 vCPU / 32GB)

```
  AWS RDS          ████████████████████  $650–800/mo
  Google Cloud     ████████████████░░░░  $550–650/mo
  Azure            ████████████████████  $600–700/mo
  DigitalOcean  ✅ ████████████░░░░░░░░    ~$480/mo  ← rẻ hơn 30–40%
  DO Self-mgmt  ✅ ██████████░░░░░░░░░░    ~$384/mo  ← rẻ nhất
```

---

## "Cao" hay không — phụ thuộc business context

```
  Revenue / tháng     Infra $480       Verdict
  ───────────────     ────────────     ──────────────────
  < $10K          →   = 5% revenue  →  ⚠️  Dùng self-managed
  ~ $50K          →   = 1% revenue  →  ✅  Bình thường
  > $200K         →   = 0.2%        →  ✅  Rất rẻ, không cần nghĩ
```

---

## 📈 Gợi ý theo giai đoạn

| Giai đoạn | MAU | Concurrent req | Setup | Chi phí/tháng |
|---|---|---|---|---|
| 🌱 **Startup / MVP** | < 1k | < 20 | DB-Large, no HA | **$60** |
| 🚀 **Growing** | < 50k | ~200–500 | DB-XL + 1 standby | **$240** |
| 🏢 **Medium** | 50k–500k | ~500–5k | DB-2XL + HA | **$480** |
| ⚡ **Scale** | > 500k | > 5k | Self-managed + Replica + PgBouncer | **$384** |

---

## Tóm lại

```
Bắt đầu: DB-XL ($240/tháng)
    │
    ├── Tối ưu query trước
    │       └── Dùng EXPLAIN ANALYZE, thêm index đúng chỗ
    │
    ├── Thêm PgBouncer khi scale instance
    │       └── $0 nếu self-host
    │
    └── Scale lên DB-2XL khi concurrent connections thực sự cần
            └── Không phải vì "sợ không đủ"
```

> 💡 Đừng over-provision ngay từ đầu.
> Bottleneck thực sự thường là **query chậm**, không phải DB nhỏ.

---

**Tiếp theo:** [active-recall.md](active-recall.md) — Kiểm tra lại toàn bộ kiến thức vừa học.
