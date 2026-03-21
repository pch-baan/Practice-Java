# 🗺️ Concurrency — Thread Lifecycle & Connection Pool

## Bản đồ tổng thể

```
🌐 Internet
      │
      ▼
⚖️ Load Balancer
      │
      ├──────────────────────────────────────────────┐
      ▼                                              ▼
┌─────────────────────────────────┐      ┌──────────────────┐
│       🟢 App Instance           │      │   🟢 App Instance │
│                                 │      │                  │
│  [ Tomcat Thread Pool ]         │      │       ...        │
│   ┌──────┬──────┬──────┐        │      └──────────────────┘
│   │ T-1  │ T-2  │ T-N  │  ...   │
│   └──┬───┴──┬───┴──┬───┘        │
│      │      │      │            │
│   Thread States per thread:     │
│   NEW → RUNNABLE → RUNNING      │
│        → BLOCKED / WAITING /    │
│          TIMED_WAITING          │
│        → TERMINATED             │
│                                 │
│  [ HikariCP Connection Pool ]   │  ← Bottleneck thực sự
│   ┌──────┬──────┬──────┐        │
│   │ C-1  │ C-2  │ C-N  │  ...   │
└───┴──┬───┴──┬───┴──┬───┴────────┘
       └──────┴──────┘
              │
              ▼
       🔀 PgBouncer       ← Tổng đài: gom nhiều app connections
              │               → ít real connections vào DB
              ▼
      🐘 PostgreSQL
       Primary (R/W)
              │
              ▼
      🐘 PostgreSQL
        Replica (R)
```

---

## 📚 Learning Path

| Bước | File | Nội dung | Tại sao đọc trước |
|---|---|---|---|
| **1** | [1-thread-states.md](1-thread-states.md) | 6 Thread States + 80/20 focus | Nền tảng — mọi thứ còn lại đều dùng khái niệm này |
| **2** | [2-tomcat.md](2-tomcat.md) | Tomcat là gì, vai trò trong Spring Boot | Hiểu ai tạo/quản lý các thread |
| **3** | [3-hikaricp-blocked.md](3-hikaricp-blocked.md) | HikariCP pool + BLOCKED demo + Throughput | Nguyên nhân phổ biến nhất gây BLOCKED |
| **4** | [4-production-architecture.md](4-production-architecture.md) | HLD + PostgreSQL config + PgBouncer | Scale thật, config production đúng |
| **5** | [5-cost-sizing.md](5-cost-sizing.md) | DigitalOcean pricing + sizing guide | Sizing DB theo business context, không phải số row |
| **6** | [active-recall.md](active-recall.md) | 20 câu hỏi tự kiểm tra | Ôn tập sau khi đọc xong 1-5 |
| **7** | [solution.md](solution.md) | Debug guide + thứ tự fix theo chi phí | Áp dụng khi production có vấn đề |

---

## ⚡ Quick Reference — 80/20

```
Khi production bị chậm:

  1. jstack <pid>
        │
        ├── Thread BLOCKED?   → HikariCP pool cạn / synchronized sai chỗ
        ├── TIMED_WAITING?    → Gọi external API chậm / slow query
        └── WAITING?          → CompletableFuture.get() bị treo

  2. Fix đúng thứ tự CHI PHÍ:
        $0 → Tối ưu query (index, rewrite)
        $0 → Tune HikariCP (fail-fast 5s, pool size đúng)
        $0 → @Async cho tác vụ không cần block
        $0 → PgBouncer
        $$ → Read Replica
       $$$ → Tăng DB spec  ← CUỐI CÙNG mới làm
```

---

## 🔑 3 Số cần nhớ

| Con số | Ý nghĩa |
|---|---|
| `pool_size / query_time` | Throughput tối đa (req/s) |
| `pool ≤ DB_CPU × 2` | Giới hạn pool size hợp lý |
| `5000ms` | connection-timeout production (fail-fast, không phải 30s mặc định) |
