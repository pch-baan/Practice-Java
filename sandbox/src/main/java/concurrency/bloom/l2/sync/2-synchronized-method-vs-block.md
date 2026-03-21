# Synchronized Method vs Block — Scope & Throughput

## "Synchronized" nghĩa là gì?

> **"Đồng bộ hóa"** — tức là: *điều phối thứ tự thực thi giữa các thread*.

Nói nôm na: đặt một **cái khóa (lock)** trước đoạn code nhạy cảm.
Tại một thời điểm, chỉ **1 thread** được giữ khóa và đi vào — những thread còn lại phải **đứng chờ (BLOCKED)**.

```
Không có synchronized:           Có synchronized:
Thread A ──┐                     Thread A ──► [vào trước, giữ khóa]
Thread B ──┼──► shared data      Thread B ──► [BLOCKED, chờ A xong]
Thread C ──┘    (data bị hỏng)   Thread C ──► [BLOCKED, chờ B xong]
```

**Lưu ý:** "đồng bộ" ở đây **khác** với "sync dữ liệu".
Ý nghĩa đúng = *serialize thứ tự chạy*, không phải *copy/replicate data*.

---

## Vấn đề cốt lõi: Lock bao nhiêu là đủ?

```
synchronized method  → lock toàn bộ method
synchronized block   → lock chỉ đoạn cần thiết
```

---

## Monitor là gì?

Mỗi Java object đều có một **monitor** (bộ giám sát nội tại) — đây chính là cái khóa mà `synchronized` dùng.

```
Java Object
┌──────────────────────────────────┐
│  Data fields (state)             │
│  Monitor ← synchronized dùng cái này để khóa │
└──────────────────────────────────┘
```

- Thread muốn vào `synchronized` block → phải **acquire monitor** (lấy khóa).
- Thread xong → **release monitor** → thread khác mới vào được.
- Nếu monitor đang bị giữ → thread mới chuyển sang trạng thái **BLOCKED**.

---

## Method lock lên gì?

### Instance method → lock trên `this`

```java
class OrderService {
    public synchronized void process(Order order) { ... }
}
```

Tương đương:

```java
public void process(Order order) {
    synchronized (this) {  // ← lock trên instance hiện tại
        ...
    }
}
```

```
OrderService instance:
┌──────────────────────────────────┐
│  Monitor (locked by Thread A)    │
│  ← toàn bộ method bị serialize  │
└──────────────────────────────────┘
```

### Static method → lock trên `Class`

```java
class Counter {
    public static synchronized void increment() { ... }
}
```

Tương đương:

```java
public static void increment() {
    synchronized (Counter.class) {  // ← lock trên class object
        ...
    }
}
```

---

## Vì sao method lock gây bottleneck?

Giả sử `process()` có 3 giai đoạn:

```
process():
  ┌─────────────────────────────────────────────────────────┐
  │ [validateInput: 10ms]  [updateDB: 5ms]  [sendEmail: 50ms]│
  └─────────────────────────────────────────────────────────┘
  ←──────────────── toàn bộ 65ms bị lock ──────────────────→
```

Chỉ `updateDB` cần thread-safe. Nhưng `synchronized method` lock hết, kể cả `validateInput` và `sendEmail`.

❓ **5 giai đoạn trong process() hay 5 thread?**
> `process()` có **3 giai đoạn** (validate → updateDB → email) — đó là việc của **1 thread**.
> 5 thread = **5 user client** cùng gửi request lên server tại một thời điểm.

```
👤 User A ──► HTTP Request ──► Thread 1 ──► process(orderA)
👤 User B ──► HTTP Request ──► Thread 2 ──► process(orderB)  ← BLOCKED
👤 User C ──► HTTP Request ──► Thread 3 ──► process(orderC)  ← BLOCKED
👤 User D ──► HTTP Request ──► Thread 4 ──► process(orderD)  ← BLOCKED
👤 User E ──► HTTP Request ──► Thread 5 ──► process(orderE)  ← BLOCKED
```

→ 5 thread gọi `process()` đồng thời (timeline thực tế):

```
         t=0      t=65     t=130    t=195    t=260    t=325
Thread 1: [=====RUN 65ms=====]
Thread 2: [WAIT  65ms-------][=====RUN 65ms=====]
Thread 3: [WAIT  130ms---------------------][=====RUN 65ms=====]
Thread 4: [WAIT  195ms--------------------------------][=====RUN 65ms=====]
Thread 5: [WAIT  260ms-----------------------------------------][=====RUN 65ms=====]
                                                                 ↑ wall clock = 325ms
```

Mỗi thread phải chờ tất cả thread trước xong mới được vào → **hoàn toàn tuần tự**.

❓ **User đầu mất 65ms, user cuối mất bao lâu?**
> User đầu tiên không phải chờ ai → **65ms**.
> User cuối phải chờ 4 người trước xong → **65ms × 5 = 325ms** (gấp 5 lần! 😱).

```
👤 User A (đầu tiên):  chờ   0ms + chạy 65ms =  65ms ✅
👤 User B:             chờ  65ms + chạy 65ms = 130ms
👤 User C:             chờ 130ms + chạy 65ms = 195ms
👤 User D:             chờ 195ms + chạy 65ms = 260ms
👤 User E (cuối):      chờ 260ms + chạy 65ms = 325ms ❌ (gấp 5 lần!)
```

Và khi scale lên:
```
   10 users →  user cuối chờ  650ms
  100 users →  user cuối chờ  6.5 giây 😬
1,000 users →  user cuối chờ 65 giây  💀 (timeout!)
```

Chỉ vì **5ms** của `updateDB` cần lock — nhưng `synchronized method` kéo lock ra **65ms**.

---

## Synchronized block — chỉ lock chỗ cần thiết

```java
class OrderService {
    private final Object inventoryLock = new Object();

    public void process(Order order) {
        validateInput(order);          // ← song song được, không cần lock

        synchronized (inventoryLock) {
            updateInventory(order);    // ← chỉ đây cần lock (5ms)
        }

        sendConfirmationEmail(order);  // ← song song được, không cần lock
    }
}
```

```
Thread 1: [validate: 10ms] [LOCK: update: 5ms] [email: 50ms]
Thread 2: [validate: 10ms]     ←đợi 5ms→      [email: 50ms]  ← chỉ blocked 5ms!
Thread 3: [validate: 10ms]         ←đợi→       [email: 50ms]
```

→ Total time gần như là 65ms thay vì 325ms.

---

## Block lock trên object khác nhau

Block có thể lock trên **bất kỳ object nào** — điều này mở ra khả năng có **nhiều lock độc lập**:

```java
class ProductService {
    private final Object inventoryLock = new Object();
    private final Object priceLock     = new Object();

    public void updateInventory() {
        synchronized (inventoryLock) { ... }  // Lock A
    }

    public void updatePrice() {
        synchronized (priceLock) { ... }      // Lock B — độc lập với Lock A!
    }
}
```

```
Thread 1 giữ inventoryLock → Thread 2 vào updatePrice() → KHÔNG bị BLOCKED
(vì 2 thread dùng 2 lock khác nhau)
```

Với `synchronized method`, cả 2 cùng lock `this` → **blocked lẫn nhau dù làm việc khác nhau**.

---

## So sánh

| | Method | Block |
|---|---|---|
| Lock lên | `this` hoặc `ClassName.class` | Bất kỳ object nào |
| Scope | Toàn bộ method | Chỉ đoạn cần thiết |
| Throughput | Thấp hơn (lock lâu hơn) | Cao hơn (lock ngắn hơn) |
| Khi nào dùng | Toàn bộ method cần thread-safe | Chỉ 1 đoạn cần thread-safe |
| Tách lock | Không (chỉ 1 lock per instance) | Có thể dùng nhiều lock |

---

## Nguyên tắc

> **Lock scope nhỏ nhất có thể** — chỉ bao những dòng code thực sự cần atomic.

Sai → `synchronized method` khi chỉ 1 đoạn nhỏ cần bảo vệ.
Đúng → `synchronized block` chỉ quanh đoạn cần thiết.
