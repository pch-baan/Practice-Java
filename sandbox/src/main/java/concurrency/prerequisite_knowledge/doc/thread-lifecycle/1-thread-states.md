# 1 — Thread States

**Thread State** = **trạng thái của một thread** tại một thời điểm trong vòng đời của nó.

---

## Câu chuyện: Nhà bếp có 1 bếp trưởng

```
👨‍🍳 Bếp trưởng = CPU  |  👩‍🍳 Đầu bếp = Thread

[Đầu bếp A] đang nấu          → RUNNING       (đang dùng bếp)
[Đầu bếp B] xếp hàng chờ      → RUNNABLE      (sẵn sàng, chờ tới lượt)
[Đầu bếp C] chờ nguyên liệu   → WAITING       (bị block, chờ signal)
[Đầu bếp D] hẹn giờ 5 phút   → TIMED_WAITING  (chờ có timeout)
[Đầu bếp E] đã về nhà         → TERMINATED    (xong việc)
[Đầu bếp F] chưa vào ca       → NEW           (chưa start)
```

---

## 6 Thread States trong Java

```
NEW ──start()──► RUNNABLE ◄────────────────────────┐
                    │                               │
                 scheduler                          │
                    │                    notify() / timeout / interrupt
                    ▼                               │
                RUNNING ──────► WAITING ────────────┘
                    │           TIMED_WAITING ───────┘
                    │           BLOCKED ─────────────┘
                    │
                    ▼
               TERMINATED
```

| State | Nghĩa | Gây ra bởi |
|---|---|---|
| **NEW** | Thread được tạo nhưng chưa `.start()` | `new Thread(...)` |
| **RUNNABLE** | Đang chạy HOẶC sẵn sàng chạy (chờ CPU) | `.start()` |
| **BLOCKED** | Chờ **lock** do thread khác đang giữ | `synchronized` |
| **WAITING** | Chờ **vô thời hạn** — chờ signal | `wait()`, `join()` |
| **TIMED_WAITING** | Chờ có **timeout** — tự dậy sau N ms | `sleep(ms)`, `wait(ms)` |
| **TERMINATED** | Đã chạy xong hoặc bị exception | method kết thúc |

---

## Biết Thread States để làm gì?

Trong Spring Boot, Tomcat quản lý thread tự động — nhưng khi production có vấn đề:

### 1. Debug ứng dụng bị chậm / treo

```bash
# Production bị chậm, chạy lệnh này
jstack <pid>
```

Output:
```
"http-nio-8080-exec-47" BLOCKED
    waiting to lock <0x000> (OrderService.java:84)
    owned by "http-nio-8080-exec-23"
```

Không biết BLOCKED là gì → không đọc được output → không fix được.

---

### 2. Chỉnh Thread Pool size đúng chỗ

```yaml
server:
  tomcat:
    threads:
      max: 200      # mặc định
      min-spare: 10
```

Pool 200 threads nhưng app vẫn chậm → vì 200 threads đang WAITING hết, chờ DB.
Tăng pool lên 500 cũng vô ích nếu bottleneck là DB connection.

---

### 3. Dùng `@Async` đúng cách

```java
// ❌ Sai — block thread Tomcat chờ email gửi xong (TIMED_WAITING 2 giây)
@PostMapping("/orders")
public ResponseEntity<?> createOrder(...) {
    orderService.create(req);
    emailService.send(req);      // thread đứng chờ 2s
    return ok();
}

// ✅ Đúng — thread Tomcat trả về ngay, email gửi ở thread khác
@PostMapping("/orders")
public ResponseEntity<?> createOrder(...) {
    orderService.create(req);
    emailService.sendAsync(req); // @Async — không block
    return ok();
}
```

---

## 80/20 — 3 State thực sự quan trọng

**NEW / RUNNABLE / TERMINATED** — Tomcat tự lo, không cần quan tâm.

Chỉ cần tập trung 3 state này:

### 🔴 BLOCKED — Phổ biến nhất và nguy hiểm nhất

```
Request A vào → lấy DB connection từ HikariCP pool ✓
Request B vào → pool hết connection → BLOCKED chờ
Request C, D, E... → xếp hàng BLOCKED
→ App treo, client timeout hàng loạt
```

**Gặp khi:** DB connection pool bị cạn kiệt, hoặc dùng `synchronized` sai chỗ.

---

### 🟡 TIMED_WAITING — Ăn hiệu năng thầm lặng

```java
// Mỗi request gọi API bên ngoài
restTemplate.exchange(paymentGatewayUrl, ...);
// Thread đang TIMED_WAITING chờ response
// 200 thread × 2 giây chờ = app không nhận request mới được
```

**Gặp khi:** Gọi external API (payment, SMS, email), query DB chậm, `Thread.sleep()` trong code.

---

### 🟠 WAITING — Ít gặp hơn nhưng khó debug

```java
// Dùng CompletableFuture sai cách
CompletableFuture<Result> future = asyncService.process();
future.get(); // ← thread WAITING vô thời hạn nếu async task bị treo
```

**Gặp khi:** Dùng `@Async`, `CompletableFuture`, message queue consumer.

---

## Bảng tóm tắt 80/20

| State | Nguyên nhân thực tế | Dấu hiệu |
|---|---|---|
| **BLOCKED** | HikariCP pool cạn, lock tranh chấp | App treo đột ngột, timeout hàng loạt |
| **TIMED_WAITING** | Gọi external API chậm, slow query | Response time tăng dần, throughput giảm |
| **WAITING** | `CompletableFuture.get()` bị treo | Một số request treo mãi không về |

---

## Tóm lại

```
Không biết Thread States          Biết Thread States
        │                               │
        ▼                               ▼
App chậm → "không biết           App chậm → jstack →
 tại sao" → restart server       thấy BLOCKED → fix đúng chỗ
```

Biết thread states = biết **đọc bản đồ** khi hệ thống có vấn đề.

---

**Tiếp theo:** [2-tomcat.md](2-tomcat.md) — Ai tạo và quản lý những thread này?
