# Synchronized — Cơ chế hoạt động (Monitor / Intrinsic Lock)

## "Synchronized mechanism" nghĩa là gì?

**"Cơ chế đồng bộ hóa"** — kiểm soát thứ tự truy cập tài nguyên chung giữa nhiều thread.

> ⚠️ **Dễ nhầm:** "synchronization" ≠ "đồng thời". Đây là 3 khái niệm khác nhau:

| Từ | Nghĩa | Ví dụ trong Java                       |
|---|---|----------------------------------------|
| **Synchronization** | Đồng bộ hóa — kiểm soát thứ tự truy cập | `synchronized`, `Lock`, `Semaphore (Tín hiệu)` |
| **Concurrent** | Đồng thời — nhiều tác vụ chạy cùng lúc | `Thread`, `ExecutorService`            |
| **Asynchronous** | Bất đồng bộ — không chờ nhau | `CompletableFuture`, `async/await`     |

---

## Câu chuyện: Phòng họp 1 chỗ

```
🏢 Công ty = JVM
🚪 Phòng họp = synchronized block/method
🔑 Chìa khóa phòng = Monitor (intrinsic lock)
👔 Mỗi object Java = có 1 chìa khóa gắn liền
```

Quy tắc duy nhất: **chỉ 1 người giữ chìa khóa** tại 1 thời điểm.

---

## Monitor là gì?

Mỗi **Java object** đều có sẵn 1 **monitor** (intrinsic lock- Khóa Nội Tại) gắn kèm.
Bạn không thấy nó trong code, nhưng nó luôn tồn tại.

```
Object myLock = new Object();

┌────────────────────────────────┐
│  myLock (Java object)          │
│  ┌──────────────────────────┐  │
│  │  Monitor (intrinsic lock)│  │
│  │  owner: null             │  │
│  │  entry-set: []           │  │
│  └──────────────────────────┘  │
└────────────────────────────────┘
```

---

## Cơ chế từng bước

### Bước 1: Thread A vào `synchronized`

```java
synchronized (myLock) {
    // Thread A đang chạy ở đây
}
```

```
Monitor của myLock:
┌──────────────────────────┐
│  owner: Thread A  ← 🔑  │
│  entry-set: []           │
└──────────────────────────┘

Thread A: RUNNABLE (đang chiếm lock)
```

### Bước 2: Thread B, C cũng muốn vào

```java
// Thread B và C cùng cố chạy:
synchronized (myLock) { ... }
```

```
Monitor của myLock:
┌──────────────────────────────────┐
│  owner: Thread A   ← 🔑         │
│  entry-set: [Thread B, Thread C] │  ← xếp hàng chờ
└──────────────────────────────────┘

Thread A: RUNNABLE
Thread B: BLOCKED  ← state lúc này
Thread C: BLOCKED  ← state lúc này
```

### Bước 3: Thread A ra khỏi `synchronized`

```
Thread A thoát khỏi synchronized block → Monitor released (phát hành)

Monitor của myLock:
┌──────────────────────────────────┐
│  owner: null       ← 🔓 trống   │
│  entry-set: [Thread B, Thread C] │
└──────────────────────────────────┘

JVM chọn 1 thread từ entry-set (không đảm bảo thứ tự!)
→ Thread B được chọn → lấy lock → RUNNABLE
→ Thread C: vẫn BLOCKED
```

---

## Reentrancy (Tái nhập cảnh) — Thread tự lock lại chính nó

`synchronized` trong Java là **reentrant (Người tái nhập cảnh)**: thread đang giữ lock có thể vào lại `synchronized` 
cùng object mà **không bị BLOCKED**.

```java
class Counter {
    synchronized void increment() {
        add(1); // gọi method synchronized khác
    }

    synchronized void add(int n) { // ← thread đang giữ lock → KHÔNG bị blocked
        count += n;
    }
}
```

Monitor track **reentrant count**:

```
Monitor của Counter instance:
┌─────────────────────────────────┐
│  owner: Thread A                │
│  reentrant-count: 2  ← vào 2 lần│
│  entry-set: []                  │
└─────────────────────────────────┘
```

→ Chỉ khi reentrant-count về 0 → lock mới thực sự released.

---

## Tóm tắt

```
┌─────────────────────────────────────────────────────────────────────┐
│  synchronized (obj) { ... }                                         │
│                                                                     │
│  1. Thread cố lấy monitor của obj                                   │
│  2. Nếu monitor trống → lấy được → RUNNABLE → chạy code bên trong  │
│  3. Nếu monitor đang bị chiếm → BLOCKED → xếp hàng entry-set       │
│  4. Thread hiện tại thoát block → monitor released                  │
│  5. JVM chọn 1 thread từ entry-set → trao monitor → RUNNABLE       │
└─────────────────────────────────────────────────────────────────────┘
```

| Khái niệm | Giải thích |
|---|---|
| Monitor | Intrinsic lock gắn với mỗi Java object |
| Entry-set | Danh sách thread đang BLOCKED chờ lock |
| Reentrant | Thread đang giữ lock có thể vào lại, không bị blocked |
| Không đảm bảo thứ tự | JVM không cam kết FIFO cho entry-set |
