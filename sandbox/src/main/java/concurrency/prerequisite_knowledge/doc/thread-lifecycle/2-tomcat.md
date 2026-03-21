# 2 — Tomcat & Thread Pool

**Tomcat** = **người gác cổng + bưu tá** của ứng dụng Java web.

---

## Câu chuyện: Quán cà phê

```
☕ KHÁCH (client)              🏪 QUÁN CÀ PHÊ (server)
      │                                │
      │   "cho 1 cà phê"              │
      │ ──── HTTP request ────────────►│
      │                           [ TOMCAT ]
      │                                │
      │                          Nhận yêu cầu
      │                          Giao cho barista (thread)
      │                          Barista pha cà phê (Spring code)
      │                          Trả về
      │                                │
      │ ◄──── HTTP response ───────────│
      │    "đây cà phê của bạn"        │
```

---

## Tomcat làm gì cụ thể?

```
🌐 Internet
      │
      │  HTTP/HTTPS
      ▼
[ Tomcat ]  ← Web Server / Servlet Container
      │
      ├── 👂 Lắng nghe port 8080
      ├── 📥 Nhận HTTP request từ client
      ├── 🔄 Parse thành HttpServletRequest object
      ├── 🧵 Chọn thread trong pool để xử lý  ← mỗi request = 1 thread
      ├── 📨 Chuyển vào Spring Boot (@Controller)
      └── 📤 Nhận kết quả → đóng gói HTTP response → trả về client

[ Spring Boot ]  ← Business Logic
      │
      ├── @Controller → xử lý routing
      ├── @Service    → business logic
      └── @Repository → database
```

---

## Mối quan hệ Tomcat vs Spring Boot

| | Tomcat | Spring Boot |
|---|---|---|
| **Vai trò** | Nhận & trả HTTP | Xử lý business logic |
| **Lo về** | Port, connection, thread pool | Controller, Service, DB |
| **Ví dụ** | Người gác cổng / lễ tân | Nhân viên bên trong |

---

## Thread Pool của Tomcat

```
Tomcat Thread Pool (default: max 200 threads)
┌────┬────┬────┬────┬────┬─────────────────────────┐
│ T1 │ T2 │ T3 │ T4 │ T5 │  ...   T200             │
└──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴─────────────────────────┘
   │    │    │    │    │
   ▼    ▼    ▼    ▼    ▼
  Req  Req  Req  Req  Req   ← mỗi thread xử lý 1 request
```

Mỗi thread xử lý 1 request từ đầu đến cuối.
Trong quá trình đó, thread có thể bị BLOCKED, WAITING, TIMED_WAITING (xem [1-thread-states.md](1-thread-states.md)).

---

## Tomcat đã được nhúng sẵn trong Spring Boot

```bash
# Chỉ cần chạy JAR — Tomcat tự khởi động, không cần cài riêng
java -jar app.jar
```

```
Ngày xưa (Spring thuần):
  Deploy .war file → Tomcat cài sẵn trên server → phức tạp 😓

Spring Boot:
  Tomcat nhúng trong JAR → java -jar → xong ✅
```

---

## Cấu hình Tomcat thread pool

```yaml
# application.yml
server:
  tomcat:
    threads:
      max: 200       # tối đa 200 thread xử lý request đồng thời
      min-spare: 10  # luôn giữ ít nhất 10 thread sẵn sàng
```

**Tại sao không tăng lên 1000?**
```
200 thread RUNNING → mỗi thread cần stack memory ~512KB
1000 thread        → ~500MB chỉ cho thread stack

Vả lại, nếu bottleneck là HikariCP (10 connections)
→ 190/200 thread vẫn sẽ BLOCKED chờ DB
→ Tăng Tomcat lên 1000 không giúp gì
```

---

## Tóm lại

```
HTTP request đến
      │
      ▼
Tomcat nhận → lấy 1 thread từ pool
      │
      ▼
Thread xử lý request (RUNNING)
      │
      ├── Cần query DB? → lấy HikariCP connection
      │       └── Pool hết? → thread BLOCKED chờ  ← đây là vấn đề
      │
      ▼
Trả kết quả → thread về pool → sẵn sàng nhận request mới
```

---

**Tiếp theo:** [3-hikaricp-blocked.md](3-hikaricp-blocked.md) — HikariCP là bottleneck thực sự như thế nào?
