# Solution — BLOCKED Thread Lifecycle

## Thứ tự xử lý khi BLOCKED xảy ra

```
Quan sát → Tìm nguyên nhân → Fix đúng chỗ
```

### Bước 1 — Diagnose trước, đừng đoán

```bash
jstack <pid>   # xem thread đang bị BLOCKED ở đâu
```

Nếu chưa biết bottleneck ở đâu mà đã "tăng DB" → **lãng phí tiền**.

---

### Bước 2 — Fix theo đúng thứ tự chi phí

| Ưu tiên | Solution | Chi phí | Tác động |
|---|---|---|---|
| **1** | Tối ưu query chậm (index, rewrite) | $0 | Throughput tăng ngay |
| **2** | Tune HikariCP (`connection-timeout: 5s`, pool size đúng) | $0 | Fail-fast, không queue dài |
| **3** | Dùng `@Async` cho tác vụ không cần block (email, log, push notification) | $0 | Giải phóng Tomcat thread |
| **4** | Thêm PgBouncer | $0 (self-host) | Scale nhiều instance không tốn thêm DB |
| **5** | Thêm Read Replica | $$ | Giảm tải cho Primary |
| **6** | Tăng DB spec (CPU/RAM) | $$$ | Chỉ làm khi 1-5 đã làm hết |

---

### Rule vàng

```
Tối ưu query 10ms → 5ms
= Throughput tăng gấp đôi

Tăng DB từ $240 lên $480/tháng
= Throughput tăng không chắc chắn
  nếu bottleneck là query chậm
```

**Tăng DB là giải pháp cuối cùng**, không phải đầu tiên.

---

## Thực hành tiếp theo (theo thứ tự)

| # | Kỹ năng | Mục tiêu |
|---|---|---|
| 1 | Đọc `EXPLAIN ANALYZE` | Hiểu query plan PostgreSQL, biết khi nào cần index |
| 2 | Viết slow query → fix bằng index | Thực hành tay với DB thật |
| 3 | Dùng `@Async` đúng cách | Tránh block Tomcat thread cho tác vụ phụ |
| 4 | Setup PgBouncer local bằng Docker | Hiểu config transaction pooling |
