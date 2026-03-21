# Multithreading — Kiến Thức Nền

---

## Storytelling: Nhà Hàng 1 Bếp vs Nhiều Bếp

Tưởng tượng bạn là ông chủ nhà hàng:

```
❌ SINGLE-THREADED (1 bếp trưởng)
─────────────────────────────────
Khách A order phở
  → Bếp nấu phở... (3 phút)
  → Bếp bưng ra
Khách B order bún bò
  → Bếp nấu bún bò... (3 phút)
  → Bếp bưng ra

Tổng: 6 phút — khách B ngồi chờ thấy bà tổ!
```

```
✅ MULTI-THREADED (3 bếp trưởng)
─────────────────────────────────
Thread 1: nấu phở     ████████
Thread 2: nấu bún bò  ████████
Thread 3: nấu cơm     ████████
                       ↑ chạy ĐỒNG THỜI

Tổng: 3 phút — tất cả xong cùng lúc!
```

---

## Các khái niệm nền cần nắm

### 1. Process vs Thread

```
┌─────────────────────────────────────┐
│  PROCESS (JVM)                      │
│  - Bộ nhớ riêng (Heap, Stack)       │
│  - 1 process = 1 ứng dụng          │
│                                     │
│  ┌───────┐  ┌───────┐  ┌───────┐   │
│  │Thread │  │Thread │  │Thread │   │
│  │  #1   │  │  #2   │  │  #3   │   │
│  │Stack  │  │Stack  │  │Stack  │   │
│  └───────┘  └───────┘  └───────┘   │
│         ↑ share Heap ↑              │
└─────────────────────────────────────┘
```

- **Process** = toàn bộ nhà hàng
- **Thread** = từng bếp trưởng
- Threads **share Heap** (dùng chung kho nguyên liệu) → đây là nguồn gốc của mọi bug concurrency!

---

### 2. Thread Lifecycle

```
NEW ──start()──→ RUNNABLE ──→ RUNNING
                    ↑              │
                    │         sleep/wait/I/O
                    │              ↓
                    └──── BLOCKED / WAITING
                                   │
                              (done/exception)
                                   ↓
                              TERMINATED
```

---

### 3. Vấn đề cốt lõi khi share dữ liệu

```java
// 2 threads cùng chạy
counter++;  // trông đơn giản nhưng thực ra là 3 bước:

Thread 1: READ  counter = 5
Thread 2: READ  counter = 5   ← đọc TRƯỚC khi Thread 1 ghi
Thread 1: ADD   5 + 1 = 6
Thread 2: ADD   5 + 1 = 6
Thread 1: WRITE counter = 6
Thread 2: WRITE counter = 6   ← mất 1 lần tăng!

Kết quả: 6 (mong đợi: 7) → Race Condition!
```

---

### 4. Bộ 3 khái niệm quan trọng nhất

| Khái niệm | Là gì | Giải pháp Java |
|---|---|---|
| **Race Condition** | 2 threads tranh nhau ghi cùng 1 biến | `synchronized`, `AtomicInteger` |
| **Deadlock** | 2 threads chờ nhau mãi mãi | Design lock ordering |
| **Visibility** | Thread A ghi nhưng Thread B không thấy | `volatile`, `synchronized` |

---

### 5. Công cụ Java giải quyết

```
Mức thấp (low-level):
  synchronized  →  khóa method/block
  volatile      →  đảm bảo visibility
  wait/notify   →  điều phối giữa threads

Mức cao (high-level, nên dùng):
  AtomicInteger      →  counter thread-safe
  ReentrantLock      →  lock linh hoạt hơn synchronized
  ConcurrentHashMap  →  Map thread-safe
  ExecutorService    →  quản lý thread pool
  CompletableFuture  →  async/non-blocking
```

---

## Bản đồ học Multithreading

```
Prerequisite (đang ở đây)
  └─ Thread lifecycle, Race condition, synchronized, volatile

Level 1 — Basics
  └─ ExecutorService, Callable, Future, ThreadPool

Level 2 — Intermediate
  └─ Lock, Semaphore, CountDownLatch, CyclicBarrier

Level 3 — Advanced
  └─ CompletableFuture, reactive, lock-free algorithms
```
