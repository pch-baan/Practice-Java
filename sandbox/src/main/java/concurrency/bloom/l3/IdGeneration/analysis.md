# 🎟️ ID Generation — Race Condition Analysis

## Câu chuyện mở đầu

Hãy tưởng tượng một **quầy vé số** ở hội chợ, có 1 bảng số duy nhất:

```
[ Số tiếp theo: 5 ]
```

Bình thường, mỗi người đến lấy số, nhân viên tăng lên 6. Nhưng hôm nay **2 nhân viên** cùng làm việc, và họ **nhìn bảng cùng lúc**:

```
Nhân viên A: nhìn bảng → thấy 5 → chưa kịp cập nhật...
Nhân viên B: nhìn bảng → thấy 5 → cập nhật lên 6 → trao vé số 5 cho khách B

Nhân viên A: (vẫn nhớ là 5) → cập nhật lên 6 → trao vé số 5 cho khách A
```

**Kết quả:** 2 người cầm cùng vé số #5 → TRÙNG SỐ! 💥

---

## 🐛 Bug: `IdGenerationBugDemo.java`

### Code bị lỗi

```java
static int nextOrderId = 1;  // "bảng số" dùng chung

// Mỗi Thread làm 2 bước này:
int id = nextOrderId;           // Bước 1: ĐỌC (nhìn bảng)
Thread.yield();                 // ← chủ ý mở rộng khoảng trống để bug xảy ra
nextOrderId = nextOrderId + 1;  // Bước 2: GHI (cập nhật bảng)
```

Vấn đề: giữa **Bước 1** và **Bước 2** — có một khoảng trống nhỏ. Thread khác có thể chen vào đúng lúc đó.

### Minh họa race condition (200 threads cùng chạy)

```
Thời gian →→→→→→→→→→→→→→→→→→→→→

Thread C10: [ĐỌC = 5] -------- [GHI = 6] → nhận id = 5
Thread C87:            [ĐỌC = 5] --------- [GHI = 6] → nhận id = 5 ← TRÙNG!

"Bảng số" = 5 → 6      (tăng đúng 1 lần, nhưng 2 người lấy cùng số)
```

### CyclicBarrier — súng hiệu xuất phát 🔫

```
Thread 1   ──┐
Thread 2   ──┤
Thread 3   ──┼──► BOOM! Tất cả xuất phát CÙNG LÚC
...          │
Thread 200 ──┘
```

Bắt tất cả bắt đầu đồng thời → **tăng tối đa xác suất va chạm**.

### Cách phát hiện bug

```java
Map<Integer, String> firstOwner = new ConcurrentHashMap<>();

String prev = firstOwner.putIfAbsent(id, customer);
// putIfAbsent: "chỉ ghi nếu key chưa tồn tại"
// Nếu prev != null → key đã có → ĐÃ TRÙNG ID!

if (prev != null) {
    System.out.println("BUG: Order #5 assigned to [C10] AND [C87]");
}
```

---

## ✅ Fix: `OrderServiceConcurrencyTest.java`

### So sánh 2 file — cùng ý tưởng, khác mục đích

```
IdGenerationBugDemo.java          OrderServiceConcurrencyTest.java
─────────────────────────────     ─────────────────────────────────
Mục đích: SHOW BUG               Mục đích: PROVE FIX
Chạy: main() thuần Java          Chạy: @SpringBootTest (có DB thật)
ID: tự đếm static counter        ID: DB sequence (@GeneratedValue)
Kết quả mong đợi: TRÙNG ID       Kết quả mong đợi: KHÔNG trùng
```

### Flow toàn cảnh

```
200 Threads ──► startGun.await() ──► BOOM!
                                       │
              ┌────────────────────────┼──────────────────────────┐
           Thread 1               Thread 2  ...              Thread 200
        placeOrder("C0")      placeOrder("C1")           placeOrder("C199")
              │                       │                           │
        repo.save()             repo.save()                 repo.save()
              │                       │                           │
              └───────────────► PostgreSQL Sequence ◄────────────┘
                                  nextval('orders_id_seq')
                                  ┌───────────────────┐
                                  │ 1, 2, 3, 4, ... 200│  ← KHÔNG BAO GIỜ TRÙNG
                                  └───────────────────┘
              │                       │                           │
         id = 1                   id = 2                    id = 200
              └───────────────► ids.size() == 200 ✅
```

### Chứng minh bằng raw JDBC

```java
// Inject DataSource → lấy Connection thật → query thẳng SQL
try (Connection conn = dataSource.getConnection()) {
    System.out.println("[PROOF] JDBC URL  : " + conn.getMetaData().getURL());
    System.out.println("[PROOF] DB product: " + conn.getMetaData().getDatabaseProductName());
    ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM orders");
    rs.next();
    System.out.println("[PROOF] Raw SQL COUNT(*) FROM orders = " + rs.getLong(1));
}
```

Output thực tế:
```
[PROOF] JDBC URL  : jdbc:postgresql://localhost:5432/practice_db   ← kết nối thật
[PROOF] DB product: PostgreSQL                                      ← không phải H2
[PROOF] Raw SQL COUNT(*) FROM orders = 200                          ← 200 rows có trong DB
```

> `ddl-auto: create-drop` → Hibernate tạo table khi Spring context khởi động, xóa khi tắt.
> Nên sau test, query thẳng vào DB sẽ thấy `relation "orders" does not exist`.

---

## 🗂️ 3 Cách sinh ID đúng trong production

### 1. DB Auto-increment — `IDENTITY`

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;   // DB tự tăng: 1, 2, 3, 4...
```

```
✅ Đơn giản, atomic, không cần config thêm
✅ DB đảm bảo không trùng dù nhiều thread
❌ ID lộ thứ tự → người ngoài đoán được "hệ thống có bao nhiêu order"
❌ Không dùng được với nhiều DB độc lập (distributed)
```

---

### 2. UUID — `UUID`

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;   // Java sinh: 550e8400-e29b-41d4-a716-446655440000
```

```
✅ Không trùng dù nhiều server, nhiều DB, nhiều JVM
✅ Không lộ thứ tự
❌ Index chậm hơn (chuỗi dài, random → B-tree fragmentation)
❌ Tốn storage hơn Long (16 bytes vs 8 bytes)
```

---

### 3. DB Sequence — `SEQUENCE`

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
@SequenceGenerator(name = "order_seq", allocationSize = 50)
private Long id;   // JPA batch lấy trước 50 số một lúc
```

```
✅ Nhanh hơn IDENTITY (ít round-trip DB hơn nhờ batch allocationSize)
✅ Thread-safe, atomic
❌ Phức tạp hơn, cần config thêm
```

---

## 📊 Tổng kết

### Tại sao DB an toàn mà Java counter thì không?

| | Java `static int` | DB IDENTITY / SEQUENCE |
|---|---|---|
| **Atomic?** | ❌ Read + Write là 2 bước riêng | ✅ `nextval()` là 1 lệnh atomic |
| **Nhiều thread?** | Bị race condition | Lock nội bộ, không trùng |
| **Nhiều JVM/server?** | Mỗi server đếm riêng → TRÙNG chắc | Tập trung 1 nơi → không trùng |
| **Restart server?** | Counter reset về 1 → TRÙNG | Sequence nhớ giá trị cuối |

### Chọn cái nào?

| Tình huống | Dùng |
|---|---|
| App đơn giản, 1 DB | `IDENTITY` |
| Cần ẩn thứ tự, distributed, microservices | `UUID` |
| High-traffic, cần performance | `SEQUENCE` |

### Quy tắc vàng

> Bao giờ cần sinh ID duy nhất trong môi trường nhiều thread → **đừng tự đếm trong Java**.
> Ủy quyền cho DB (`IDENTITY` / `SEQUENCE`) hoặc dùng `UUID`.

---

## ⚙️ Cấu hình test

```yaml
# sandbox/src/test/resources/application.yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/practice_db
    username: practice_user
    password: practice_pass
  jpa:
    hibernate:
      ddl-auto: create-drop   # tạo table khi test bắt đầu, xóa khi test kết thúc
```

> ⚠️ Integration test cần DB thật đang chạy. Khởi động trước: `docker-compose up postgres -d`
