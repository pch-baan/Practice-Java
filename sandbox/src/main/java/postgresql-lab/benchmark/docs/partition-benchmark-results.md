# PostgreSQL Partition Benchmark Results
**Table:** `orders` (~5,000,005 rows) + `order_items` (~15,000,015 rows)
**Date:** 2026-03-28
**Environment:** PostgreSQL 16, Docker local

---

## Setup

| | Baseline | LIST by status | RANGE by created_at |
|---|---|---|---|
| Schema | Regular table, `id BIGSERIAL PK` | LIST partition per status value | RANGE partition monthly |
| Index | Partial index `status = 'PENDING'` | `created_at DESC` per partition | Partial index per partition + `created_at DESC` per partition |
| FK | `order_items.order_id → orders(id)` | N/A (lab schema) | Dropped (PostgreSQL limitation: FK target must include partition key) |

---

## Results

### Bảng so sánh — 3 approaches

| Query | Baseline | LIST by status | RANGE by created_at |
|-------|----------|----------------|---------------------|
| B1a: Fetch PENDING + date 30 ngày | 244ms | — | **0.6ms** (400x) |
| B1b: Fetch PENDING (no date filter) | 263ms | **0.4ms** | 1.1ms |
| B2: UPDATE 1 row (PENDING → CONFIRMED) | 0.38ms | 0.9ms | 1.2ms |
| **B3: Bulk UPDATE 1000 rows** | **7.8ms** | **1,018ms** ❌ | **57.8ms** |
| B4: Date-range COUNT/SUM 30 ngày | 228ms | 257ms | **30ms** (7.6x) |
| B5: Date + PENDING LIMIT 50 | 244ms | **0.08ms** | 0.3ms |
| B6: Monthly aggregation GROUP BY | 3,066ms | 598ms | **679ms** (4.5x) |

---

## Phân tích chi tiết

### B1 — Fetch PENDING

**Baseline (263ms):**
```
Parallel Bitmap Heap Scan on orders
  Heap Blocks: exact=14456  lossy=11105   ← work_mem không đủ → lossy mode
  Rows Removed by Index Recheck: 581,908  ← phải recheck 581K rows thừa
  Workers: 2
```

**RANGE Partition (1.1ms):**
```
Merge Append
  → Index Scan on orders_y2026m03  ← chỉ đọc partition hiện tại
  → Index Scan on orders_y2026m02
  (các partition cũ: 0 rows returned, bỏ qua ngay)
```

Partition pruning + index scan per partition → không còn lossy bitmap, không recheck.

---

### B4 — Date-range scan (30 ngày gần nhất)

**Baseline (228ms):**
```
Parallel Seq Scan on orders
  Filter: created_at >= NOW() - INTERVAL '30 days'
  Rows Removed by Filter: 1,598,524   ← đọc 5M rows để lấy ~204K
```

**RANGE Partition (30ms):**
```
Append
  → Seq Scan on orders_y2026m02  (~192K rows)
  → Seq Scan on orders_y2026m03  (~186K rows)
  (24 partitions còn lại: không scan)
```

PostgreSQL biết 30 ngày gần nhất chỉ nằm trong 2 partitions (Feb + Mar 2026).

---

### B5 — Date + PENDING (best case)

**Baseline (244ms):**
```
Parallel Bitmap Heap Scan
  Lossy blocks: 11,518 → recheck 581,908 rows
  Filter created_at removes 319,610 more rows
```

**RANGE Partition (0.3ms):**
```
Limit
  → Index Scan on idx_orders_y2026m03_pending  ← 1 partition + partial index
     (chỉ đọc PENDING rows trong tháng 3/2026)
```

Đây là hot path của worker-service (poll PENDING orders) — **từ 244ms xuống 0.3ms**.

---

### B6 — Monthly aggregation

**Baseline (3,066ms):**
```
Seq Scan → Sort
  Sort Method: external merge  Disk: 167,288 kB  ← 167MB temp file trên disk!
  5M rows sort không vừa RAM → tràn ra disk
```

**RANGE Partition (679ms):**
```
Append
  → HashAggregate on orders_y2024m03
  → HashAggregate on orders_y2024m04
  ...
  (mỗi partition ~200K rows → vừa RAM, không cần disk sort)
```

Không còn external disk sort. Mỗi partition aggregate độc lập trong RAM.

---

### B2/B3 — UPDATE (trade-off quan trọng nhất)

**B2 UPDATE 1 row:**
```
Baseline:        0.38ms
LIST partition:  0.93ms   ← 2.4x chậm hơn (DELETE + INSERT cross-partition)
RANGE partition: 1.2ms    ← in-place, overhead nhỏ do partition routing
```
Với 1 row, cả 3 đều chấp nhận được.

**B3 Bulk UPDATE 1000 rows — đây là bằng chứng loại LIST:**
```
Baseline:        7.8ms
LIST partition:  1,018ms  ← 130x chậm hơn! ❌
RANGE partition: 57.8ms   ← 7x chậm hơn (chấp nhận được)
```

LIST partition chậm 130x vì:
```
UPDATE 1000 rows: PENDING → CONFIRMED
= 1000 × DELETE từ orders_pending
+ 1000 × INSERT vào orders_confirmed
= 2,000 write operations thay vì 1,000
+ index maintenance trên cả 2 partitions
```

RANGE partition chậm 7x vì subquery tìm 1000 PENDING rows phải scan qua 27 partitions (không có date filter). Fix: thêm `AND created_at >= NOW() - INTERVAL '7 days'` → prune partitions → về ~10ms.

---

## Kết luận

### Tại sao loại LIST partition by status

B3 (bulk UPDATE 1000 rows) chậm **130x** — từ 7.8ms lên 1,018ms. Đây là dealbreaker vì `orders` có lifecycle thay đổi status liên tục (PENDING → CONFIRMED → SHIPPED → DELIVERED). Mỗi transition = cross-partition row movement. Ở production với hàng nghìn đơn hàng được xử lý mỗi giờ, overhead này sẽ trở thành bottleneck nghiêm trọng.

LIST partition chỉ phù hợp khi data **không bao giờ đổi partition** sau khi insert — ví dụ: partition by `country_code`, `tenant_id`, hay `product_category`.

---

### RANGE partition by `created_at` phù hợp với `orders` vì:

1. **Hot path (worker poll PENDING)** cải thiện 400-800x — worker-service chỉ quan tâm PENDING gần đây
2. **Aggregation** hết disk sort — 3s → 679ms
3. **UPDATE không cross partition** — `created_at` là immutable sau khi tạo đơn hàng
4. **Data lifecycle tự nhiên** — đơn hàng cũ (>1 năm) có thể `DETACH PARTITION` để archive

### Trade-off cần biết:

| Trade-off | Mức độ | Giải pháp |
|-----------|--------|-----------|
| Bulk UPDATE chậm hơn 7x (57ms) | Thấp | Thêm date filter vào query |
| FK `order_items → orders` bị drop | Trung bình | Enforce ở application layer (JPA) |
| Phải thêm partition hàng tháng | Thấp | Script maintenance `04_add_monthly_partition.sql` |
| PK đổi từ `(id)` → `(id, created_at)` | Trung bình | Application query by `id` vẫn hoạt động |

---

## Raw numbers

### Baseline

| Query | Scan | Blocks read | Rows wasted | Time |
|-------|------|-------------|-------------|------|
| B1b | Parallel Bitmap Heap Scan | read=75,677 | Recheck: 581,908 | 263ms |
| B4 | Parallel Seq Scan | read=59,573 | Filter: 1,598,524 | 228ms |
| B5 | Parallel Bitmap Heap Scan | read=76,449 | Recheck+Filter: 901K | 244ms |
| B6 | Seq Scan + Disk Sort 167MB | read=59,368 | — | 3,066ms |

### LIST partition by status

| Query | Scan | Partitions scanned | Time |
|-------|------|--------------------|------|
| B1b | Seq Scan on orders_pending | 1 of 5 | 0.4ms |
| B2 | DELETE (pending) + INSERT (confirmed) | 1+1 | 0.9ms |
| B3 | 1000× DELETE + 1000× INSERT | 2 | **1,018ms** ❌ |
| B4 | Seq Scan | 5 of 5 (no pruning) | 257ms |
| B5 | Index Scan on orders_pending | 1 of 5 | 0.08ms |
| B6 | HashAggregate | 5 of 5 | 598ms |

### RANGE partition by created_at

| Query | Scan | Partitions scanned | Time |
|-------|------|--------------------|------|
| B1a | Merge Append + Index Scan | 2 of 31 | 0.6ms |
| B1b | Merge Append + Index Scan | 31 (all) | 1.1ms |
| B2 | Index Scan (in-place) | 1 | 1.2ms |
| B3 | Bitmap Index Scan (multi-partition) | 27 | 57.8ms |
| B4 | Append + Seq Scan | 2 of 31 | 30ms |
| B5 | Index Scan | 1 of 31 | 0.3ms |
| B6 | Append + HashAggregate | 31 (all) | 679ms |
