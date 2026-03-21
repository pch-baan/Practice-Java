# Active Recall — L2 Understand: "Why + How it works"

Mức này cần **giải thích cơ chế**, không chỉ đọc tên. Che đáp án, tự giải thích, rồi mở ra kiểm tra.

---

## Topic 1: synchronized — Cơ chế Monitor/Lock

### Câu 1
**Monitor là gì? Nó gắn với thứ gì trong Java?**

<details>
<summary>Đáp án</summary>

Mỗi **Java object** đều có 1 **monitor** (intrinsic lock - khóa nội tại) gắn sẵn — không nhìn thấy trong 
code nhưng luôn tồn tại.

```
Object myLock = new Object();

┌──────────────────────────────┐
│  myLock (Java object)        │
│  ┌────────────────────────┐  │
│  │  Monitor               │  │
│  │  owner: null           │  │
│  │  entry-set: []         │  │
│  └────────────────────────┘  │
└──────────────────────────────┘
```

`synchronized (myLock)` → thread cố lấy monitor của `myLock`.

</details>

---

### Câu 2
**Giải thích từng bước: Thread A giữ lock, Thread B cố vào → điều gì xảy ra với monitor và state của B?**

<details>
<summary>Đáp án</summary>

```
Bước 1 — Thread A vào synchronized:
  Monitor: owner = Thread A, entry-set = []
  Thread A: RUNNABLE

Bước 2 — Thread B cố vào cùng synchronized:
  Thread B cố lấy monitor → đã bị A chiếm
  Monitor: owner = Thread A, entry-set = [Thread B]
  Thread B: BLOCKED  ← state thay đổi ngay

Bước 3 — Thread A thoát synchronized:
  Monitor: owner = null, entry-set = [Thread B]
  JVM chọn Thread B → trao monitor
  Thread B: RUNNABLE
```

Điểm mấu chốt: BLOCKED xảy ra tại thời điểm thread cố lấy lock đang bị chiếm.

</details>

---

### Câu 3
**`synchronized` trong Java là reentrant (Người tái nhập cảnh) nghĩa là gì? Tại sao điều này quan trọng?**

<details>
<summary>Đáp án</summary>

Reentrant = **thread đang giữ lock có thể vào lại synchronized cùng object mà không bị BLOCKED**.

```java
class Counter {
    synchronized void increment() {
        add(1);  // gọi synchronized method khác cùng class
    }
    synchronized void add(int n) {
        count += n;  // Thread không bị blocked ở đây!
    }
}
```

Monitor track **reentrant-count**:
```
increment() vào: reentrant-count = 1
add() vào:       reentrant-count = 2
add() thoát:     reentrant-count = 1
increment() thoát: reentrant-count = 0 → lock released
```

Nếu không reentrant → thread tự deadlock khi gọi synchronized method khác.

</details>

---

### Câu 4
**JVM có đảm bảo thứ tự FIFO khi nhiều thread BLOCKED chờ 1 lock không?**

<details>
<summary>Đáp án</summary>

**Không.** JVM không cam kết thứ tự FIFO cho entry-set.

Thread đến trước có thể không được vào trước — JVM/OS tự quyết định tùy implementation.

→ Nếu cần fairness (FIFO), dùng `ReentrantLock(true)` (L5 topic).

</details>

---

## Topic 2: synchronized Method vs Block

### Câu 5
**`synchronized` instance method lock lên thứ gì? Viết tương đương dạng block.**

<details>
<summary>Đáp án</summary>

Lock lên **`this`** — instance hiện tại.

```java
// Method:
public synchronized void process() { ... }

// Tương đương block:
public void process() {
    synchronized (this) {  // ← lock trên instance
        ...
    }
}
```

`static synchronized` → lock lên `ClassName.class`.

</details>

---

### Câu 6
**Tại sao synchronized method thường gây bottleneck hơn block? Cho ví dụ số cụ thể.**

<details>
<summary>Đáp án</summary>

Method lock **toàn bộ method** kể cả phần không cần thread-safe.

```
process() = [validateInput: 10ms] [updateDB: 5ms] [sendEmail: 50ms]

synchronized method → lock 65ms
synchronized block  → chỉ lock 5ms (phần updateDB)
```

5 thread gọi đồng thời:
```
Method: 65ms × 5 = 325ms total (tất cả chờ nhau)
Block:  ~65ms total  (chỉ chờ 5ms phần DB)
```

→ Throughput block cao hơn nhiều vì lock ngắn hơn = ít contention hơn.

</details>

---

### Câu 7
**Trong synchronized block, có thể dùng 2 lock khác nhau cho 2 tác vụ không liên quan không? Điều này giúp gì?**

<details>
<summary>Đáp án</summary>

Có. Block có thể lock trên **bất kỳ object nào**:

```java
class ProductService {
    private final Object inventoryLock = new Object();
    private final Object priceLock     = new Object();

    public void updateInventory() {
        synchronized (inventoryLock) { ... }
    }

    public void updatePrice() {
        synchronized (priceLock) { ... }  // độc lập!
    }
}
```

Thread 1 đang `updateInventory` và Thread 2 đang `updatePrice` → **không blocked lẫn nhau**.

Với `synchronized method` (cùng lock `this`) → chúng sẽ blocked lẫn nhau dù làm việc khác nhau.

</details>

---

## Topic 3: Happens-Before

### Câu 8
**Tại sao Thread A ghi `x = 1` nhưng Thread B có thể đọc được `x = 0`? Giải thích cơ chế phần cứng.**

<details>
<summary>Đáp án</summary>

Vì CPU hiện đại có **cache riêng** cho mỗi core:

```
Thread A (core 1)           Thread B (core 2)
┌───────────┐               ┌───────────┐
│ Cache L1  │               │ Cache L1  │
│ x = 1 ✍️ │               │ x = 0 👀  │  ← B đọc từ cache riêng
└─────┬─────┘               └─────┬─────┘
      │                           │
┌─────▼───────────────────────────▼─────┐
│              RAM: x = 0               │  ← A chưa flush lên RAM
└───────────────────────────────────────┘
```

A ghi vào cache của mình nhưng chưa flush lên RAM → B đọc cache của B → thấy giá trị cũ.

Ngoài ra, compiler/JIT có thể **reorder** instructions để tối ưu.

</details>

---

### Câu 9
**Happens-before (A hb→ B) đảm bảo gì? Nó KHÔNG đảm bảo gì?**

<details>
<summary>Đáp án</summary>

**Đảm bảo:**
1. A thực thi trước B
2. Mọi thứ A **ghi** đều được B **nhìn thấy** (visibility guarantee)

**KHÔNG đảm bảo:**
- Không có hb → không đảm bảo visibility
- Volatile đảm bảo visibility nhưng **không đảm bảo atomicity** (`count++` vẫn race)

```
happens-before = "cam kết về visibility"

A hb→ B: B thấy tất cả những gì A đã ghi trước điểm A hb→ B
```

</details>

---

### Câu 10
**Monitor Lock Rule (synchronized) tạo ra happens-before như thế nào?**

<details>
<summary>Đáp án</summary>

**Unlock** của monitor M **happens-before** mọi **lock** tiếp theo của M.

```
Thread A:                      Thread B:
synchronized (lock) {          synchronized (lock) {
    x = 1;                         y = x;  ← đảm bảo thấy x = 1 ✅
}  ← UNLOCK                    }
      │
      └─── hb ──────────────→ LOCK (Thread B)
```

Timeline:
```
A: [lock][x=1][unlock]
                   ↓ hb
B:                 [lock][read x → 1 ✅][unlock]
```

→ `synchronized` không chỉ ngăn race condition mà còn **đảm bảo B thấy giá trị mới nhất**.

</details>

---

### Câu 11
**Volatile Rule tạo happens-before như thế nào? Volatile đảm bảo những gì, không đảm bảo gì?**

<details>
<summary>Đáp án</summary>

**Write** vào volatile **happens-before** mọi **read** tiếp theo của variable đó.

```java
volatile boolean ready = false;
int x = 0;

// Thread A:
x = 42;
ready = true;   // ← volatile write hb→ read ở B

// Thread B:
if (ready) {    // ← volatile read
    print(x);   // thấy x = 42 ✅
}
```

**Volatile đảm bảo:** Visibility (mọi thread đọc giá trị mới nhất).

**Volatile KHÔNG đảm bảo:** Atomicity.
```java
// KHÔNG an toàn dù volatile:
volatile int count = 0;
count++;  // đọc → cộng → ghi — 3 bước không atomic, vẫn race!
```

</details>

---

### Câu 12
**Data race là gì? Cho ví dụ và giải thích tại sao xảy ra.**

<details>
<summary>Đáp án</summary>

Data race xảy ra khi:
1. Ít nhất 2 thread access **cùng variable**
2. Ít nhất 1 thread **ghi**
3. **Không có happens-before** giữa chúng

```java
int count = 0;

// Thread A và B chạy song song:
void increment() { count++; }
```

`count++` không phải 1 bước, mà là 3:
```
Thread A: read(count=0) → compute(1) → write(count=1)
Thread B: read(count=0) → compute(1) → write(count=1)
                                                     ↑ Lost update!
Kết quả: count = 1 thay vì 2
```

Fix:
```java
synchronized void increment() { count++; }
// unlock A hb→ lock B → B đọc count mới nhất ✅
```

</details>

---

### Câu 13
**Thread Start Rule và Thread Join Rule đảm bảo gì?**

<details>
<summary>Đáp án</summary>

**Start Rule:** `Thread.start()` hb→ mọi action trong thread đó.

```java
x = 10;
Thread t = new Thread(() -> {
    print(x);  // đảm bảo thấy x = 10 ✅
});
t.start();  // ← hb→ mọi thứ trong t
```

**Join Rule:** Mọi action trong thread hb→ `Thread.join()` returns.

```java
// Thread t đã ghi y = 20
t.join();  // ← mọi thứ trong t hb→ đây
print(y);  // đảm bảo thấy y = 20 ✅
```

→ `join()` là cách đảm bảo visibility khi cần kết quả từ thread con.

</details>

---

### Câu 14
**Thread C không dùng synchronized có thấy giá trị Thread A ghi trong synchronized không?**

<details>
<summary>Đáp án</summary>

**Không đảm bảo.**

Visibility qua `synchronized` chỉ hoạt động giữa các thread **dùng cùng lock**:

```
Thread A: synchronized (lock) { x = 1; }  → unlock
Thread B: synchronized (lock) { read x; }  → hb, thấy x = 1 ✅
Thread C: // không synchronized             → không hb, có thể thấy x = 0 ❌
```

Thread C muốn thấy `x = 1` → phải cũng dùng `synchronized (lock)` hoặc khai báo `x` là `volatile`.

</details>

---

Xong L2 khi trả lời trơn tru 14 câu này, đặc biệt câu 2, 6, 8, 10 (cần giải thích cơ chế đầy đủ) → chuyển sang **L3 Apply**.
