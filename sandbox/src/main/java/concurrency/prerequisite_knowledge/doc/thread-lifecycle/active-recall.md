# Active Recall — BLOCKED Thread Lifecycle

Cách dùng: Đọc câu hỏi → **che đáp án → tự trả lời** → mới mở ra kiểm tra.

---

### BLOCK 1 — Thread States cơ bản

**Q1.** Java có bao nhiêu thread state? Liệt kê hết.

<details>
<summary>Đáp án</summary>

6 state: **NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED**

</details>

---

**Q2.** `BLOCKED` vs `WAITING` — khác nhau điểm gì cốt lõi?

<details>
<summary>Đáp án</summary>

- **BLOCKED** — chờ **lock** (synchronized). Thread *muốn chạy* nhưng bị chặn bởi thread khác đang giữ lock.
- **WAITING** — chờ **signal** từ thread khác (`notify()`). Thread *chủ động nhường* CPU.

</details>

---

**Q3.** Gọi `Thread.sleep(5000)` → thread ở state nào?

<details>
<summary>Đáp án</summary>

**TIMED_WAITING** — chờ có timeout, tự dậy sau 5 giây.

</details>

---

**Q4.** `CompletableFuture.get()` không có timeout → thread ở state nào? Nguy hiểm gì?

<details>
<summary>Đáp án</summary>

**WAITING** — chờ vô thời hạn. Nếu async task bị treo → thread treo mãi mãi → resource leak.

</details>

---

**Q5.** Tool nào dùng để xem thread state khi production bị chậm?

<details>
<summary>Đáp án</summary>

```bash
jstack <pid>
```
Output sẽ cho thấy từng thread đang ở state nào và đang chờ lock/resource gì.

</details>

---

### BLOCK 2 — HikariCP Connection Pool

**Q6.** HikariCP default pool size là bao nhiêu? Ý nghĩa là gì?

<details>
<summary>Đáp án</summary>

**10 connections** — chỉ 10 thread được query DB cùng lúc. Thread thứ 11 trở đi → BLOCKED chờ.

</details>

---

**Q7.** HikariCP `connection-timeout` mặc định là bao nhiêu? Production nên chỉnh thành bao nhiêu và tại sao?

<details>
<summary>Đáp án</summary>

- Mặc định: **30,000ms (30 giây)**
- Production nên: **5,000ms (5 giây)** — fail-fast, tránh request xếp hàng dài. Client biết lỗi sớm hơn thay vì chờ 30s mới timeout.

</details>

---

**Q8.** Log lỗi này xuất hiện nghĩa là gì, cần fix gì?

```
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

<details>
<summary>Đáp án</summary>

**HikariCP pool bị cạn** — thread chờ connection quá `connection-timeout`.

Fix theo 2 hướng:
1. **Tăng pool size** (`maximum-pool-size`) nếu DB server còn chịu được
2. **Tối ưu query** chậm → trả connection về pool sớm hơn

</details>

---

**Q9.** Pool = 10, query mất 500ms. Throughput tối đa là bao nhiêu req/s?

<details>
<summary>Đáp án</summary>

```
Throughput = pool_size / query_time = 10 / 0.5s = 20 req/s
```

</details>

---

**Q10.** Tại sao tăng pool từ 10 lên 200 không nhất thiết giúp app nhanh hơn?

<details>
<summary>Đáp án</summary>

PostgreSQL xử lý concurrent query tối đa bằng **số CPU core**. Vượt quá đó → DB phải context-switch → overhead tăng → query chậm hơn dù pool lớn hơn.

**Quy tắc:** `pool_size ≤ DB_CPU_cores × 2`

</details>

---

### BLOCK 3 — synchronized Bottleneck

**Q11.** Code này có vấn đề gì? Fix thế nào?

```java
@Service
class OrderService {
    public synchronized void updateInventory(int qty) {
        // logic phức tạp, chạy 500ms
    }
}
```

<details>
<summary>Đáp án</summary>

**Vấn đề:** `synchronized` trên method của `@Service` → chỉ 1 Tomcat thread chạy được cùng lúc → throughput = 1 req / 500ms = 2 req/s dù Tomcat có 200 thread.

**Fix:** Dùng `synchronized` ở phạm vi hẹp hơn (chỉ block cần protect), hoặc dùng `ReentrantLock`, hoặc transaction DB thay vì lock Java.

</details>

---

**Q12.** Khi nào nên dùng `synchronized` trong Spring Boot backend?

<details>
<summary>Đáp án</summary>

Hầu như **không nên** dùng `synchronized` trên @Service method. Thay vào đó:
- **Concurrent reads:** Không cần lock
- **Race condition trên DB:** Dùng DB transaction + `SELECT FOR UPDATE`
- **In-memory counter:** Dùng `AtomicInteger`, `AtomicLong`
- **Complex critical section:** Dùng `ReentrantLock` với scope nhỏ nhất có thể

</details>

---

### BLOCK 4 — Production Architecture

**Q13.** Tại sao cần PgBouncer? Không dùng thì sao?

<details>
<summary>Đáp án</summary>

**Không có PgBouncer:**
- 10 app instance × 20 HikariCP = 200 real connections vào DB
- PostgreSQL phải context-switch 200 connections → overhead lớn → chậm

**Có PgBouncer:**
- 200 app connections → PgBouncer gom → chỉ 80 real connections vào DB
- DB chỉ thấy 80 connections dù ngoài có bao nhiêu instance

</details>

---

**Q14.** PgBouncer có 3 pool mode. Mode nào dùng cho production API? Tại sao?

<details>
<summary>Đáp án</summary>

**Transaction pooling** — connection chỉ giữ trong 1 transaction, trả về pool ngay sau khi commit/rollback. Phù hợp API vì mỗi request thường chỉ cần 1 transaction ngắn.

(Session pooling giữ connection suốt session → lãng phí. Statement pooling quá chi tiết, ít dùng.)

</details>

---

**Q15.** 3 app instance, mỗi instance HikariCP pool = 20. PgBouncer pool nên để bao nhiêu?

<details>
<summary>Đáp án</summary>

3 × 20 = 60 app connections vào PgBouncer. PgBouncer real pool ra DB nên ≤ `DB_CPU × 2`.

Ví dụ DB 8 cores → real pool = **80** (buffer thêm chút so với 60, nhưng không quá lớn).

</details>

---

### BLOCK 5 — Sizing thực tế

**Q16.** Company có 50,000 MAU. DB cần bao nhiêu concurrent connections?

<details>
<summary>Đáp án</summary>

```
50k MAU → ~5k DAU → ~200-500 concurrent request lúc peak
```

**Không phải 50k connections** — MAU là người dùng mỗi tháng, không phải người dùng cùng lúc. Sizing DB dựa trên **concurrent request**, không phải số row hay MAU.

</details>

---

**Q17.** App có 1 triệu row trong bảng `users`. Cần DB lớn không?

<details>
<summary>Đáp án</summary>

**Không nhất thiết.** `SELECT WHERE id = ?` với index → < 1ms dù bảng có 1 triệu row.

Cần DB lớn khi:
- Concurrent connections cao
- Query phức tạp (JOIN nhiều bảng, không có index)
- Write throughput lớn (nhiều INSERT/UPDATE/DELETE)
- Hot data không vừa RAM (shared_buffers)

</details>

---

**Q18.** Khi nào nên dùng Managed DB (DigitalOcean Managed PostgreSQL) vs Self-managed?

<details>
<summary>Đáp án</summary>

| | Managed | Self-managed |
|---|---|---|
| **Nên dùng khi** | Team nhỏ, không có DBA | Có senior DevOps/DBA |
| **Ưu điểm** | Auto backup, failover, monitoring | Rẻ hơn 2-2.5x |
| **Nhược điểm** | Đắt hơn | Tự lo backup, Patroni, Grafana |

</details>

---

### BLOCK 6 — Tổng hợp / Senior mindset

**Q19.** Production báo "app chậm bất thường". Bước debug đầu tiên của bạn là gì?

<details>
<summary>Đáp án</summary>

```bash
jstack <pid>
```

Đọc output → tìm thread đang BLOCKED/WAITING ở đâu → xác định bottleneck (HikariCP? synchronized? external API call?) → fix đúng gốc.

</details>

---

**Q20.** Tomcat thread pool 200, HikariCP pool 10. Bottleneck ở đâu khi traffic cao?

<details>
<summary>Đáp án</summary>

**HikariCP (10 << 200)** là bottleneck. 200 Tomcat thread có thể nhận request, nhưng chỉ 10 thread được query DB cùng lúc. 190 thread còn lại BLOCKED chờ connection. Tăng Tomcat lên 500 cũng vô ích nếu không tăng DB pool hoặc tối ưu query.

</details>
