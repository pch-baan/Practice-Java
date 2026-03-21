# 👟 Read-Modify-Write — Race Condition & Optimistic Locking

## Câu chuyện mở đầu

Flash sale: **1 đôi giày Limited Edition** duy nhất. 50 người cùng lúc bấm "Mua ngay".

```
Kho: [👟] ← chỉ còn 1

Khách A: kiểm tra → còn hàng! → [đang chuẩn bị mua...]
                                          ↕ khách B chen vào
Khách B: kiểm tra → còn hàng! →                          → trừ kho → MUA ĐƯỢC!

Khách A:                         (vẫn nghĩ còn hàng) → trừ kho → MUA ĐƯỢC!

Kết quả: 2 người mua, kho âm 💥 OVERSELL!
```

---

## 🐛 Bug: `StockOversellBugDemo.java`

### Pattern sai — Read-Modify-Write không atomic

```java
static volatile int stock = 1;

static void purchase(String customer) {
    if (stock >= 1) {        // Bước 1: READ  — "còn hàng không?"
        Thread.yield();      // ← race window mở ra tại đây
        stock = stock - 1;   // Bước 2: MODIFY + WRITE — "trừ kho"
        soldCount.incrementAndGet();
    }
}
```

3 bước **Read → Modify → Write** KHÔNG phải 1 thao tác atomic.
Thread khác có thể chen vào giữa **Read** và **Write**.

### Minh họa race window

```
Thời gian →→→→→→→→→→→→→→→→→→→→→→→→→→→

Thread A: [READ: stock=1, OK] ─────────────── [WRITE: stock=0] → SOLD!
Thread B:                     [READ: stock=1, OK] ── [WRITE: stock=0] → SOLD!

stock: 1 ──────────────────────────────────────────────────► -1 hoặc 0
soldCount: 0 ────────────────────────────────────────────────► 2
```

Kết quả: `soldCount=2` nhưng `initialStock=1` → **OVERSELL!**

---

## ✅ Fix: Optimistic Locking với `@Version`

### Cơ chế hoạt động

```java
@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int stock;

    @Version          // ← chìa khóa của Optimistic Locking
    private Long version;
}
```

`@Version` thêm 1 cột `version` vào DB. Mỗi lần UPDATE, JPA tự generate:

```sql
-- Thay vì:
UPDATE products SET stock = 0 WHERE id = 1

-- JPA generate:
UPDATE products SET stock = 0, version = 1 WHERE id = 1 AND version = 0
--                                                         ↑ điều kiện version!
```

Nếu `version` đã bị thread khác thay đổi → **0 rows updated** → JPA throw `ObjectOptimisticLockingFailureException`.

### Flow đầy đủ với 50 threads

```
50 Threads ──► startGun.await() ──► BOOM!
                     │
     ┌───────────────┼──────────────────┐
  Thread 1        Thread 2  ...     Thread 50
     │                │                  │
  READ product     READ product       READ product
  (stock=1,        (stock=1,          (stock=1,
   version=0)       version=0)         version=0)
     │                │                  │
  stock > 0?       stock > 0?         stock > 0?
     ✅               ✅                 ✅
     │                │                  │
  stock = 0        stock = 0          stock = 0
     │                │                  │
  COMMIT? ◄──────────────────────────────┘
     │
  UPDATE ... WHERE id=1 AND version=0
     │
  ┌──┴─────────────────────┐
  │ 1 thread thắng          │ 49 threads thua
  │ version=0 → version=1   │ version đã là 1 → 0 rows updated
  │ → commit ✅             │ → ObjectOptimisticLockingFailureException ❌
  └─────────────────────────┘
```

### Service xử lý

```java
@Transactional
public boolean purchase(Long productId) {
    Product product = repo.findById(productId).orElseThrow();

    if (product.getStock() <= 0) {
        return false;   // out of stock → trả về false ngay
    }

    product.setStock(product.getStock() - 1);
    repo.save(product);
    // ↑ Hibernate CHƯA execute SQL ở đây
    // SQL thực sự chạy khi @Transactional commit (method return)
    // Nếu version conflict lúc commit → exception trả về caller
    return true;
}
```

---

## 🔍 Output thực tế (50 threads, stock = 1)

```
[PRODUCTION] Product #1 purchased. Remaining: 0   ← 10 threads vượt qua stock check
[PRODUCTION] Product #1 purchased. Remaining: 0
... (10 lần)
[PRODUCTION] Product #1 — out of stock            ← 40 threads thấy stock=0 ngay
... (40 lần)

[PRODUCTION] Purchased : 1   ← chỉ 1 commit thành công
[PRODUCTION] Rejected  : 49  ← 9 version conflict + 40 out of stock
[PRODUCTION] Final stock: 0
[PRODUCTION] Invariant: purchased(1) + remaining(0) = 1 == initial stock (1) ✅
```

### Tại sao 10 thread log "purchased" nhưng chỉ 1 thành công?

```
@Transactional hoạt động qua AOP proxy:

Thread A calls purchase() ──► Spring Proxy ──► actual method runs
                                                  │
                                             log "purchased"  ← xảy ra TRƯỚC commit
                                             return true
                                                  │
                                             Spring commits transaction
                                                  │
                                    ┌─────────────┴──────────────┐
                              version OK                    version conflict
                              → caller nhận true            → proxy throws exception
                              → successCount++              → caller nhận exception
                                                            → rejectedCount++
```

Log "purchased" xuất hiện 10 lần vì 10 thread ĐÃ xử lý xong method body.
Nhưng chỉ 1 transaction commit được — 9 cái còn lại bị rollback và throw exception.

---

## ⚔️ Optimistic vs Pessimistic Locking

```
OPTIMISTIC (dùng @Version)           PESSIMISTIC (dùng SELECT FOR UPDATE)
─────────────────────────────         ──────────────────────────────────────
Không lock DB khi đọc                Lock DB ngay khi đọc
Thread đọc thoải mái                 Thread phải chờ lock
Conflict phát hiện lúc commit        Conflict không thể xảy ra (blocked)
Phù hợp: ít conflict                 Phù hợp: nhiều conflict, cần chắc chắn
Lỗi: OptimisticLockingFailure        Lỗi: Deadlock (nếu không cẩn thận)
Caller phải retry hoặc báo lỗi       DB tự xử lý thứ tự
```

```
OPTIMISTIC:                          PESSIMISTIC:
T1: READ ─────────────── WRITE ✅    T1: READ+LOCK ──────── WRITE → UNLOCK
T2: READ ─────── WRITE ❌ retry      T2:           WAIT ──────────────────── READ+LOCK ── WRITE
T3: READ ── WRITE ❌ retry           T3:                    WAIT ─────────────────────────────── ...
```

---

## 📊 Tổng kết

### Tại sao Optimistic Locking an toàn?

|                     | Không có lock | `@Version` Optimistic |
|---------------------|---|---|
| **Race condition?** | ✅ Có thể xảy ra | ❌ Không thể (version check) |
| **Oversell? (Bán nhiều hơn số hàng có trong kho)**    | ✅ Xảy ra | ❌ Không thể |
| **DB lock?**        | Không | Không (chỉ check version lúc commit) |
| **Throughput?**     | Cao nhưng sai | Cao và đúng |
| **Khi conflict?**   | Dữ liệu sai | Exception → caller retry/báo lỗi |

### Invariant bất biến

```
purchased + remaining = initialStock
    1     +     0     =      1        ✅ LUÔN ĐÚNG
```

Dù 50 thread cùng chạy, invariant không bao giờ bị vi phạm.

### Quy tắc vàng

> Bất cứ khi nào có pattern **"đọc → kiểm tra → ghi"** trên dữ liệu chia sẻ —
> thao tác đó KHÔNG atomic và cần được bảo vệ.
>
> Với JPA: dùng `@Version` (Optimistic) hoặc `@Lock(PESSIMISTIC_WRITE)` (Pessimistic).
> Không bao giờ tự check-then-act mà không có cơ chế lock.

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
      ddl-auto: create-drop
```

> ⚠️ Integration test cần DB thật. Khởi động trước: `docker-compose up postgres -d`
