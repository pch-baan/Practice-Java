# Happens-Before — JMM Visibility

## Vấn đề: Thread đọc giá trị cũ

Nghe có vẻ vô lý: Thread A ghi `x = 1`, Thread B đọc `x` → B có thể thấy `x = 0`!

**Tại sao?** Vì CPU hiện đại có nhiều tầng cache:

```
         Thread A                     Thread B
         (CPU core 1)                 (CPU core 2)
         ┌───────────┐               ┌───────────┐
         │  Cache L1 │               │  Cache L1 │
         │  x = 1    │               │  x = 0    │  ← B đọc từ cache riêng!
         └─────┬─────┘               └─────┬─────┘
               │                           │
         ┌─────▼───────────────────────────▼─────┐
         │              RAM: x = 0               │  ← chưa flush lên
         └───────────────────────────────────────┘
```
> "Flush" = đẩy/ghi ngược dữ liệu từ cache xuống RAM (main memory)


A ghi `x = 1` vào cache của A nhưng **chưa flush lên RAM** → B đọc `x = 0` từ cache của B.

Ngoài cache còn có vấn đề **compiler/JIT reordering**: trình biên dịch có thể sắp xếp lại thứ tự lệnh để tối ưu hiệu năng.

---

## Happens-Before là gì?

**JMM (Java Memory Model)** định nghĩa: nếu **A happens-before B**, thì:
- Mọi thứ A ghi, B **được đảm bảo thấy**.

Không có happens-before → **không có gì đảm bảo** B thấy gì.

```
happens-before = "cam kết về visibility"

A hb→ B  nghĩa là:
  1. A thực thi trước B
  2. Kết quả ghi của A được B nhìn thấy
```

---

## 4 Rule quan trọng nhất

### Rule 1: Program Order (Within a Thread)

Trong 1 thread, mọi action A đều happens-before action B nếu A trước B trong code.

```java
// Thread A:
x = 1;   // A hb→ B (within same thread, luôn đúng)
y = x;   // B — thấy x = 1
```

*Bình thường, không cần nghĩ. Chỉ phức tạp khi cross-thread.*

---

### Rule 2: Monitor Lock (synchronized)

**Unlock** của monitor M happens-before **lock** tiếp theo của M.

```
Thread A:                    Thread B:
synchronized (lock) {        synchronized (lock) {
    x = 1;                       y = x;   ← đảm bảo thấy x = 1
}  // ← UNLOCK                }
↓
hb→ Thread B LOCK
```

```
Timeline:
A: [lock][x=1][unlock]
                   ↓ hb
B:                  [lock][read x → 1 ✅][unlock]
```

→ Đây là lý do `synchronized` không chỉ ngăn data race mà còn **đảm bảo visibility**.

---

### Rule 3: Volatile Write/Read

**Write** vào volatile variable happens-before **read** tiếp theo của variable đó.

```java
volatile boolean ready = false;
int x = 0;

// Thread A:
x = 42;
ready = true;  // ← volatile write — hb→ read của ready ở B

// Thread B:
if (ready) {   // ← volatile read
    System.out.println(x);  // đảm bảo thấy x = 42 ✅
}
```

*Volatile chỉ đảm bảo **visibility**, không đảm bảo **atomicity** (x++ vẫn không an toàn).*

---

### Rule 4: Thread Start/Join

- `Thread.start()` happens-before mọi action trong thread đó.
- Mọi action trong thread happens-before `Thread.join()` returns.

```java
x = 10;
Thread t = new Thread(() -> {
    System.out.println(x);  // đảm bảo thấy x = 10 ✅
});
t.start();  // ← start() hb→ mọi thứ trong t

t.join();   // ← mọi thứ trong t hb→ đây
System.out.println("thread done"); // thấy tất cả những gì t ghi
```

---

## Data Race xảy ra khi nào?

Khi 2 thread access **cùng variable**, **ít nhất 1 thread ghi**, và **không có happens-before** giữa chúng.

```java
// KHÔNG AN TOÀN — không có happens-before
class Counter {
    int count = 0;  // không volatile, không synchronized

    void increment() {
        count++;  // Thread A và B đọc-ghi count
    }
}
```

```
Thread A:  read count=0 → compute 1 → write count=1
Thread B:  read count=0 → compute 1 → write count=1
                                                    ↑ Lost update!
Kết quả: count = 1 thay vì 2
```

Fix với `synchronized`:
```java
synchronized void increment() {
    count++;  // unlock A hb→ lock B → B thấy count mới nhất ✅
}
```

---

## Tóm tắt

```
┌─────────────────────────────────────────────────────────────┐
│  Happens-Before Rules                                       │
│                                                             │
│  1. Program order:  A trước B trong code (same thread)     │
│  2. Monitor lock:   unlock hb→ lock tiếp theo              │
│  3. Volatile:       write hb→ read tiếp theo               │
│  4. Thread start:   start() hb→ mọi action trong thread    │
│     Thread join:    mọi action hb→ join() returns          │
└─────────────────────────────────────────────────────────────┘
```

| Cơ chế | Đảm bảo | Không đảm bảo |
|---|---|---|
| `synchronized` | Visibility + Atomicity | — |
| `volatile` | Visibility | Atomicity (x++ vẫn race) |
| Không cơ chế | Không gì cả | Cả hai |

---

## Liên hệ thực tế

`synchronized` tốt nhưng **chỉ đảm bảo visibility với thread cũng dùng cùng lock**:

```java
// Thread A: synchronized (lock) { x = 1; }  → unlock
// Thread B: synchronized (lock) { read x; }  → thấy x=1 ✅
// Thread C: KHÔNG synchronized → đọc x → có thể thấy x=0 ❌
```

→ Visibility chỉ được đảm bảo giữa các thread **dùng cùng lock**.
