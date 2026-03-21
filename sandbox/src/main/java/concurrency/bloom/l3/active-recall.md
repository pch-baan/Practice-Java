# Active Recall — L3 Apply: "How to fix — Áp dụng vào code thật"

Mức này không hỏi định nghĩa hay cơ chế nữa. Bạn phải **đọc code thật, nhận ra bug, và fix**.

Format mỗi bài:
1. Đọc code bị lỗi
2. Tự trả lời câu hỏi (che đáp án lại)
3. Mở đáp án → kiểm tra + chạy code để xác nhận

---

## Bài 1 — Race Condition: OrderService

→ Xem chi tiết tại [`OrderServiceBugDemo/active-recall.md`](OrderServiceBugDemo/active-recall.md)

**Files:**
- `OrderServiceBugDemo.java` — demo bug với CyclicBarrier + Thread.yield()
- `OrderServiceFixed_Sync.java` — fix bằng `synchronized`
- `OrderServiceFixed_Atomic.java` — fix bằng `AtomicInteger`

---

## Bài 2 — Synchronized Method Bottleneck: ProductService

→ Xem chi tiết tại [`SynchronizedMethodBottleneck/active-recall.md`](SynchronizedMethodBottleneck/active-recall.md)

**Files:**
- `ProductServiceBugDemo.java` — demo bottleneck: 5 thread × 1002ms = ~5010ms
- `ProductServiceFixed.java` — fix bằng `synchronized block` (chỉ lock 2ms critical section)
- `ProductServiceBenchmark.java` — so sánh SLOW vs FAST trực tiếp

---

## Bài 3 — Visibility Bug: FeatureFlag Cache

**Bối cảnh:** Hệ thống dùng feature flag để bật/tắt tính năng. Background thread load config từ DB mỗi 60s.
**Bug report:** "Sau khi admin tắt feature trong DB, app vẫn chạy tính năng đó hàng giờ đồng hồ."

```java
public class FeatureFlagCache {
    private boolean loaded = false;
    private Map<String, Boolean> flags = new HashMap<>();

    // Chạy trong background thread mỗi 60s
    public void reloadFromDB() {
        Map<String, Boolean> newFlags = loadFromDatabase();
        flags = newFlags;        // Thread A ghi
        loaded = true;           // Thread A ghi
    }

    // Gọi từ request threads
    public boolean isEnabled(String featureName) {
        if (!loaded) return false;    // Thread B đọc — có thể stale!
        return flags.getOrDefault(featureName, false);  // Thread B đọc
    }

    private Map<String, Boolean> loadFromDatabase() {
        // giả lập DB query
        return Map.of("new-checkout", true, "dark-mode", false);
    }
}
```

**Câu hỏi:**
1. Bug xảy ra như thế nào? Giải thích cụ thể bằng CPU cache model.
2. Tại sao restart app thì hết bug, nhưng sau vài giờ lại xuất hiện?
3. Fix bằng `volatile`. Volatile đủ không, hay cần gì thêm?

<details>
<summary>Đáp án</summary>

#### 1. Bug: CPU cache visibility

Không có happens-before giữa Thread A (background) và Thread B (request):

```
Core 1 (background thread):          Core 2 (request thread):
Cache: loaded=true, flags={...}       Cache: loaded=false  ← stale!

Thread A ghi loaded=true vào cache của Core 1.
Core 1 chưa flush lên RAM.
Thread B đọc loaded từ cache của Core 2 → thấy false mãi.
```

Kết quả: Thread B thấy `loaded = false` dù background đã reload xong → luôn return false.

#### 2. Tại sao restart hết bug?

Khi app mới start, `reloadFromDB()` chạy ngay → cả hệ thống đều thấy `loaded=true` vì chưa có cache cũ. Sau vài giờ, giá trị trong cache của request thread bị "frozen" — CPU không flush vì không có memory barrier → stale mãi.

#### 3. Fix bằng `volatile`

```java
public class FeatureFlagCacheFixed {
    private volatile boolean loaded = false;           // ← volatile
    private volatile Map<String, Boolean> flags = new HashMap<>();  // ← volatile

    public void reloadFromDB() {
        Map<String, Boolean> newFlags = loadFromDatabase();
        flags = newFlags;   // volatile write — hb→ mọi volatile read sau
        loaded = true;       // volatile write
    }

    public boolean isEnabled(String featureName) {
        if (!loaded) return false;   // volatile read — thấy giá trị mới nhất
        return flags.getOrDefault(featureName, false);
    }
}
```

**Volatile đủ không?**

`volatile` đủ cho **visibility** — Thread B sẽ thấy `loaded=true` sau khi Thread A ghi.

Nhưng có race condition tinh tế hơn:
```
Thread A: flags = newFlags;   // bước 1
Thread B: if (!loaded)...     // đọc loaded=false → skip flags
Thread A: loaded = true;      // bước 2
Thread B: // không biết flags vừa được cập nhật
```

→ Thứ tự ghi quan trọng: phải ghi `flags` trước `loaded`:
```java
flags = newFlags;   // ghi data trước
loaded = true;       // ghi flag sau — khi B thấy loaded=true, flags đã sẵn sàng
```

Với `volatile`, mọi write trước volatile write đều visible sau volatile read → ordering đúng.

**Nếu cần atomic update cả `flags` lẫn `loaded`**, dùng `synchronized` hoặc `AtomicReference`:

```java
public class FeatureFlagCacheBest {
    private final AtomicReference<Map<String, Boolean>> flags =
        new AtomicReference<>(Collections.emptyMap());

    public void reloadFromDB() {
        Map<String, Boolean> newFlags = loadFromDatabase();
        flags.set(Collections.unmodifiableMap(newFlags));  // atomic swap
    }

    public boolean isEnabled(String featureName) {
        return flags.get().getOrDefault(featureName, false);  // luôn đọc consistent snapshot
    }
}
```

→ Không cần `loaded` flag nữa — `flags` luôn chứa snapshot mới nhất (bắt đầu từ empty map).

</details>

---

## Bài 4 — Deadlock: BankTransferService

**Bối cảnh:** Core banking. Mỗi transfer lock 2 account để đảm bảo consistency.
**Bug report:** "Hệ thống đôi khi đơ hoàn toàn, thread dump cho thấy tất cả transaction thread bị BLOCKED lẫn nhau."

```java
public class Account {
    final int id;
    int balance;
    Account(int id, int balance) { this.id = id; this.balance = balance; }
}

public class BankTransferService {
    public void transfer(Account from, Account to, int amount) {
        synchronized (from) {              // lock account nguồn trước
            synchronized (to) {            // lock account đích sau
                if (from.balance < amount) throw new IllegalStateException("Insufficient");
                from.balance -= amount;
                to.balance   += amount;
                System.out.println("Transferred " + amount + " from #" + from.id + " to #" + to.id);
            }
        }
    }
}
```

```
// Deadlock scenario:
Thread 1: transfer(accountA, accountB, 100)
Thread 2: transfer(accountB, accountA, 200)

Thread 1: lock(A) → chờ lock(B)
Thread 2: lock(B) → chờ lock(A)
→ DEADLOCK: cả 2 chờ nhau mãi mãi
```

**Câu hỏi:**
1. Giải thích deadlock này xảy ra như thế nào? Vẽ ra (text) điều kiện chờ vòng tròn.
2. Fix bằng **canonical lock order** (lock theo thứ tự cố định).
3. Có cách nào fix mà không cần lock 2 object không?

<details>
<summary>Đáp án</summary>

#### 1. Điều kiện deadlock

Deadlock xảy ra khi có **circular wait**:

```
Thread 1: giữ Lock(A) ──────────────────────────────────────────┐
          chờ Lock(B) ←──────────────────────────── Thread 2 giữ Lock(B)
                                                     Thread 2 chờ Lock(A)
```

4 điều kiện Coffman (tất cả đều đúng ở đây):
1. **Mutual exclusion**: synchronized block chỉ cho 1 thread
2. **Hold and wait**: Thread 1 giữ A trong khi chờ B
3. **No preemption**: không ai có thể cướp lock của thread khác
4. **Circular wait**: A→B→A

#### 2. Fix: Canonical Lock Order

Luôn lock theo thứ tự ID tăng dần, bất kể `from` hay `to`:

```java
public class BankTransferServiceFixed {
    public void transfer(Account from, Account to, int amount) {
        // Xác định thứ tự lock dựa trên ID (không phải from/to)
        Account first  = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to   : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance < amount) throw new IllegalStateException("Insufficient");
                from.balance -= amount;
                to.balance   += amount;
            }
        }
    }
}
```

```
Thread 1: transfer(A→B): first=A, second=B → lock(A) → lock(B)
Thread 2: transfer(B→A): first=A, second=B → lock(A) → lock(B)

Cả 2 đều lock theo thứ tự A→B → không bao giờ có circular wait.
```

#### 3. Demo deadlock và fix (chạy được)

```java
public class DeadlockDemo {
    static Account accountA = new Account(1, 1000);
    static Account accountB = new Account(2, 1000);

    public static void main(String[] args) {
        // BUGGY — uncommenting this will deadlock
        // Thread t1 = new Thread(() -> transferBuggy(accountA, accountB, 100));
        // Thread t2 = new Thread(() -> transferBuggy(accountB, accountA, 200));

        // FIXED
        Thread t1 = new Thread(() -> transferFixed(accountA, accountB, 100));
        Thread t2 = new Thread(() -> transferFixed(accountB, accountA, 200));

        t1.start();
        t2.start();

        try {
            t1.join(5000);
            t2.join(5000);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.println("A balance: " + accountA.balance);  // Expected: 900
        System.out.println("B balance: " + accountB.balance);  // Expected: 1100
    }

    static void transferBuggy(Account from, Account to, int amount) {
        synchronized (from) {
            sleep(100);  // tăng xác suất deadlock
            synchronized (to) {
                from.balance -= amount;
                to.balance   += amount;
            }
        }
    }

    static void transferFixed(Account from, Account to, int amount) {
        Account first  = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to   : from;
        synchronized (first) {
            sleep(100);
            synchronized (second) {
                from.balance -= amount;
                to.balance   += amount;
            }
        }
    }

    static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

class Account {
    final int id;
    int balance;
    Account(int id, int balance) { this.id = id; this.balance = balance; }
}
```

#### 4. Alternative: ReentrantLock với tryLock timeout

```java
public class BankTransferServiceTryLock {
    public boolean transfer(Account from, Account to, int amount, long timeoutMs)
            throws InterruptedException {
        ReentrantLock lockFrom = getLock(from);
        ReentrantLock lockTo   = getLock(to);

        if (lockFrom.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
            try {
                if (lockTo.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                    try {
                        from.balance -= amount;
                        to.balance   += amount;
                        return true;
                    } finally {
                        lockTo.unlock();
                    }
                }
            } finally {
                lockFrom.unlock();
            }
        }
        return false;  // timeout → caller có thể retry
    }

    private final Map<Integer, ReentrantLock> locks = new ConcurrentHashMap<>();
    private ReentrantLock getLock(Account a) {
        return locks.computeIfAbsent(a.id, id -> new ReentrantLock());
    }
}
```

→ `tryLock` với timeout: nếu không lấy được lock trong X ms → trả về false thay vì deadlock mãi.
→ Trade-off: caller phải xử lý retry logic.

</details>

---

## Bảng tóm tắt L3

| Bài | Bug | Root Cause | Fix |
|---|---|---|---|
| 1 — OrderService | Duplicate order ID | `nextOrderId++` không atomic (read-modify-write race) | `AtomicInteger.getAndIncrement()` |
| 2 — ProductService | Low throughput | `synchronized method` giữ lock 102ms, chỉ cần 2ms | Refactor sang `synchronized block` cho phần shared state |
| 3 — FeatureFlag | Stale feature config | Non-volatile flag → CPU cache, không có happens-before | `volatile` field + đúng thứ tự ghi |
| 4 — BankTransfer | Deadlock | Circular wait — lock thứ tự ngược nhau | Canonical lock order (lock theo ID tăng dần) |

---

Xong L3 khi có thể:
- Nhìn vào code, chỉ ra đúng dòng bị lỗi và giải thích tại sao
- Viết được fix code đúng (chạy được)
- Giải thích được trade-off giữa các cách fix

→ Chuyển sang **L4 Analyze**: "Tại sao code này bị lỗi? Đọc thread dump, tìm root cause từ triệu chứng."
