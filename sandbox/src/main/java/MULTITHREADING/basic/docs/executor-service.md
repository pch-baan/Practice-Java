# ExecutorService — Quản Lý Thread Pool

---

## Storytelling: Từ "tự thuê bếp" → "thuê quản lý bếp"

**Cách cũ — tự tạo Thread thủ công:**

```
Khách đến → Bạn chạy ra đường tìm bếp mới → Bếp nấu xong → Bếp nghỉ việc luôn
Khách khác → Lại chạy ra đường tìm bếp mới...

Vấn đề:
  - Tốn thời gian tạo/hủy thread liên tục
  - Không kiểm soát được có bao nhiêu thread đang chạy
  - 10,000 request = 10,000 threads → hệ thống sập!
```

**Cách mới — ExecutorService (thuê quản lý bếp):**

```
Bạn thuê 1 Quản Lý Bếp, Quản Lý có sẵn 5 bếp trưởng chờ việc

Khách đến → Quản Lý phân công bếp rảnh → Bếp nấu xong → Bếp chờ việc tiếp

10,000 request → vẫn chỉ 5 bếp trưởng, xử lý tuần tự theo hàng đợi!
```

---

## Cấu trúc hoạt động

```
                  ┌─────────────────────────────┐
submit(task) ──→  │       Task Queue             │
submit(task) ──→  │  [t1] [t2] [t3] [t4] [t5]  │
submit(task) ──→  │       (hàng đợi)             │
                  └──────────────┬──────────────┘
                                 │ phân công
                  ┌──────────────▼──────────────┐
                  │       Thread Pool            │
                  │  [T1] [T2] [T3] [T4] [T5]  │
                  │   (5 threads sẵn sàng)       │
                  └─────────────────────────────┘
```

---

## Code thực tế

### Cách cũ (đừng làm thế này)
```java
// Tạo thread mới cho mỗi task — nguy hiểm!
for (int i = 0; i < 1000; i++) {
    new Thread(() -> processOrder(i)).start(); // 1000 threads!
}
```

### Cách mới — ExecutorService
```java
// 1. Tạo pool với 5 threads
ExecutorService executor = Executors.newFixedThreadPool(5);

// 2. Submit tasks — không cần quan tâm thread nào chạy
for (int i = 0; i < 1000; i++) {
    executor.submit(() -> processOrder(i)); // chỉ 5 threads, xử lý lần lượt
}

// 3. Đóng pool sau khi xong
executor.shutdown();
```

---

## 3 loại Pool phổ biến

| Pool | Cách tạo | Dùng khi | Thực tế |
|---|---|---|---|
| **Fixed** | `newFixedThreadPool(5)` | Biết trước số lượng task ổn định | ✅ Phổ biến — REST API, batch job |
| **Cached** | `newCachedThreadPool()` | Task ngắn, số lượng thay đổi liên tục | ⚠️ Dùng cẩn thận — dễ OOM nếu task tăng đột biến |
| **Single** | `newSingleThreadExecutor()` | Muốn 1 thread, đảm bảo thứ tự | ✅ Event log, audit trail |
| **Virtual** | `newVirtualThreadPerTaskExecutor()` | I/O-heavy, hàng nghìn task đồng thời | 🔥 Java 21+ — xu hướng thay thế Fixed cho I/O |

---

## Callable + Future — lấy kết quả trả về

`Runnable` không trả về kết quả. Cần kết quả → dùng `Callable + Future`:

```java
ExecutorService executor = Executors.newFixedThreadPool(3);

// submit Callable → nhận Future (giấy hẹn)
Future<Integer> future = executor.submit(() -> {
    Thread.sleep(2000); // giả lập tính toán nặng
    return 42;
});

System.out.println("Đang làm việc khác...");

// get() sẽ BLOCK cho đến khi có kết quả
Integer result = future.get(); // → 42
System.out.println("Kết quả: " + result);
```

```
Timeline:
  Main  ──submit──→ [làm việc khác] ──get()──WAIT──→ [có kết quả] ──→ tiếp tục
  Thread                             [tính toán 2s]──────────────→ return 42
```

---

## Tóm tắt

```
Thread thủ công      ExecutorService
─────────────────    ────────────────────────────
Tự tạo/hủy          Pool tái sử dụng thread
Không kiểm soát      Giới hạn số thread
Không lấy kết quả   Callable + Future
Không hàng đợi       Built-in Task Queue
```
