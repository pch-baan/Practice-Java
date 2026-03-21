# Active Recall — L1 Remember: "What là gì? / Liệt kê"

Mức này chỉ cần **nhớ đúng định nghĩa và danh sách**. Che đáp án, tự trả lời, rồi mở ra kiểm tra.

---

### Câu 0 — Tổng quát
**Thread là gì? Mô tả toàn bộ vòng đời của một Thread từ đầu đến cuối.**

<details>
<summary>Đáp án</summary>

#### Câu chuyện: Nhà hàng 1 bếp trưởng

```
🏪 App của bạn = Nhà hàng
👨‍🍳 CPU core    = Bếp trưởng (chỉ 1 người nấu tại 1 thời điểm)
👩‍🍳 Thread      = Đầu bếp phụ (mỗi task chạy riêng 1 người)
📋 Task        = Đơn hàng cần nấu
```

Nhà hàng nhận **nhiều đơn cùng lúc** — không thể chờ đơn này xong mới nhận đơn kia.
→ Giải pháp: **thuê nhiều đầu bếp phụ** = tạo nhiều Thread.

---

#### Vòng đời đầy đủ

```
🥚 Mới tuyển vào
   Thread t = new Thread(...)
   → Chưa vào bếp, ngồi chờ

        .start() ← "vào ca đi"
           │
           ▼
🏃 Đang chạy / xếp hàng chờ bếp
   RUNNABLE
   → Sẵn sàng nấu, hoặc đang nấu
   → CPU scheduler (bộ lập lịch) quyết định ai được dùng bếp

           │
     ┌─────┼─────┐
     ▼     ▼     ▼

🚧 Chờ nguyên liệu bị team khác giữ
   BLOCKED
   → "Tủ lạnh đang bị khóa, tôi đứng chờ"

😴 Chủ động ngồi nghỉ, chờ đồng nghiệp gọi
   WAITING
   → "Gọi tôi khi đơn mới đến"

⏰ Đặt đồng hồ nghỉ 5 phút
   TIMED_WAITING
   → "Tôi nghỉ 5 phút, tự dậy"

           │
           ▼
💀 Xong ca, về nhà
   TERMINATED
```

---

#### Từng state giải thích thật chậm

**🥚 NEW**
```
Thread t = new Thread(() -> nau());
// Đầu bếp được tuyển nhưng chưa vào ca
// Chưa tốn CPU, chưa làm gì cả
t.getState() → NEW
```

**🏃 RUNNABLE**
```
t.start();  ← "bắt đầu ca làm việc"

CPU chỉ có 1 bếp → nhiều thread xếp hàng:
┌─────────────────────────────┐
│  🏃A  🏃B  🏃C  🏃D  🏃E  │  ← đều là RUNNABLE
│   ↑                         │
│  A đang dùng CPU            │
│  B, C, D, E chờ tới lượt   │
└─────────────────────────────┘
Java gom "đang chạy" + "chờ CPU" chung thành RUNNABLE
```

**🚧 BLOCKED**
```
Thread A vào synchronized block trước → giữ khóa 🔒
Thread B đến sau → 🚧 BLOCKED — đứng ngoài cửa

Khác WAITING ở chỗ:
  BLOCKED  = bị chặn từ bên ngoài (lock)
  WAITING  = tự chủ động nghỉ (chờ signal)
```

**😴 WAITING**
```
signal.wait();  ← "tôi tự ngủ, gọi tôi khi cần"

⚠️  Nguy hiểm: nếu không ai gọi notify() → ngủ mãi mãi
⚠️  CompletableFuture.get() không timeout → tương tự
```

**⏰ TIMED_WAITING**
```
Thread.sleep(5000);  ← "tôi nghỉ đúng 5 giây"

0s ──────────────── 5s
                     ↑
               tự thức dậy, không cần ai gọi
An toàn hơn WAITING vì có timeout bảo vệ
```

**💀 TERMINATED**
```
void run() {
    nau();
    // hết method → thread chết
}
Một khi TERMINATED → không start() lại được
Muốn làm tiếp → tạo Thread mới
```

---

#### Bảng tóm tắt

```
┌─────────────────┬────────────────────────┬───────────────────┐
│ State           │ Giống như              │ Gây ra bởi        │
├─────────────────┼────────────────────────┼───────────────────┤
│ 🥚 NEW          │ Được tuyển, chưa vào ca│ new Thread()      │
│ 🏃 RUNNABLE     │ Đang nấu / xếp hàng   │ .start()          │
│ 🚧 BLOCKED      │ Chờ tủ lạnh mở khóa   │ synchronized      │
│ 😴 WAITING      │ Tự ngủ, chờ đồng nghiệp│ wait(), join()   │
│ ⏰ TIMED_WAITING│ Hẹn giờ ngủ 5 phút    │ sleep(ms)         │
│ 💀 TERMINATED   │ Xong ca, về nhà        │ method kết thúc   │
└─────────────────┴────────────────────────┴───────────────────┘
```

#### Thần chú nhớ 6 state
```
"New Rabbits Block Wait, Timer's Done"
  N    R      B    W     T       D
  NEW  RUN  BLOCK WAIT TIMED  TERMINATED
```

</details>

---

### Câu 1
**Java có bao nhiêu thread state? Liệt kê tất cả.**

<details>
<summary>Đáp án</summary>

**6 state:**

```
🥚 NEW           → chưa start
🏃 RUNNABLE      → đang chạy / sẵn sàng
🚧 BLOCKED       → chờ lock
😴 WAITING       → chờ signal (vô thời hạn)
⏰ TIMED_WAITING → chờ có timeout
💀 TERMINATED    → xong việc
```

</details>

---

### Câu 2
**`BLOCKED` là gì? Gây ra bởi điều gì?**

<details>
<summary>Đáp án</summary>

```
Thread A giữ lock 🔒
        │
        ▼
Thread B muốn vào synchronized block
        │
        ▼
🚧 BLOCKED — đứng chờ bên ngoài, không làm gì được
```

Gây ra bởi: `synchronized`.

</details>

---

### Câu 3
**`WAITING` là gì? Gây ra bởi điều gì?**

<details>
<summary>Đáp án</summary>

```
Thread B gọi wait() → 😴 chủ động ngủ
        │
        ▼
Chờ mãi... chờ mãi...
        │
        ▼
Thread A gọi notify() → 🔔 mới thức dậy
```

Gây ra bởi: `wait()`, `join()`.

</details>

---

### Câu 4
**`TIMED_WAITING` là gì? Gây ra bởi điều gì?**

<details>
<summary>Đáp án</summary>

```
Thread gọi sleep(5000)
        │
        ▼
⏰ Đặt đồng hồ 5 giây → ngủ
        │
        ▼
Hết giờ → tự thức dậy 🔔 (không cần ai notify)
```

Gây ra bởi: `sleep(ms)`, `wait(ms)`.

</details>

---

### Câu 5
**`RUNNABLE` nghĩa là gì?**

<details>
<summary>Đáp án</summary>

```
🏃 Thread A  ──► đang dùng CPU  (RUNNING)
🏃 Thread B  ──► xếp hàng chờ  (READY)
🏃 Thread C  ──► xếp hàng chờ  (READY)
         └──────────────────────────┘
              đều gọi là RUNNABLE
```

Thread **đang chạy HOẶC sẵn sàng chạy** — Java gom chung 1 state.

</details>

---

### Câu 6
**`NEW` và `TERMINATED` là gì?**

<details>
<summary>Đáp án</summary>

```
🥚 NEW
   Thread t = new Thread(...)
   Chưa gọi .start() → chưa có gì xảy ra

        .start()
           │
           ▼
        [chạy...]

           │
           ▼
💀 TERMINATED
   Method kết thúc hoặc bị exception → thread chết
```

</details>

---

### Câu 7
**HikariCP là gì? Tại sao cần Connection Pool?**

<details>
<summary>Đáp án</summary>

#### Không có Pool — tốn kém mỗi request

```
Request 1 → [Tạo kết nối DB] → Query → [Đóng kết nối]  ❌ chậm
Request 2 → [Tạo kết nối DB] → Query → [Đóng kết nối]  ❌ chậm
```

Mỗi lần tạo kết nối = TCP handshake + auth + setup → **rất chậm và tốn CPU**.

#### Có Connection Pool — tái sử dụng

```
          ┌─────────────────────────────────┐
          │     HikariCP Pool (10 conns)    │
          │  [🔗1] [🔗2] [🔗3] ... [🔗10] │
          └─────────────────────────────────┘
Request 1 → lấy 🔗1 → Query → trả lại pool  ✅ nhanh
Request 2 → lấy 🔗2 → Query → trả lại pool  ✅ nhanh
Request 3 → lấy 🔗1 (dùng lại) → ...        ✅ nhanh
```

**HikariCP** = thư viện Java quản lý Connection Pool cho database.
- **Hikari** (光) = ánh sáng tiếng Nhật → ý nghĩa: cực nhanh
- **CP** = Connection Pool
- Default của **Spring Boot 2+**

```
┌───────────────┬────────────────────────────┐
│ Không có Pool │ Tạo + đóng kết nối mỗi lần │
│ Có Pool       │ Tái sử dụng kết nối sẵn có │
└───────────────┴────────────────────────────┘
```

</details>

---

### Câu 8
**HikariCP default pool size là bao nhiêu?**

<details>
<summary>Đáp án</summary>

```
HikariCP Pool
┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐
│🔗│🔗│🔗│🔗│🔗│🔗│🔗│🔗│🔗│🔗│  ← 10 connections
└──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘

Thread thứ 11 đến → 🚧 BLOCKED chờ
```

**10 connections.**

</details>

---

### Câu 9
**HikariCP `connection-timeout` mặc định là bao nhiêu?**

<details>
<summary>Đáp án</summary>

```
Thread bị BLOCKED chờ connection...

0s ──────────────────────────── 30s
                                 │
                                 ▼
                     💥 HikariPool timeout exception
```

**30,000ms (30 giây).**

</details>

---

### Câu 10
**Tool nào dùng để xem thread state trên production?**

<details>
<summary>Đáp án</summary>

```bash
jstack <pid>
```

```
Output:
"http-nio-8080-exec-47" 🚧 BLOCKED
    waiting to lock <0x000> (OrderService.java:84)
    owned by "http-nio-8080-exec-23"
```

</details>

---

### Câu 11
**`BLOCKED` khác `WAITING` điểm gì cốt lõi nhất?**

<details>
<summary>Đáp án</summary>

```
🚧 BLOCKED                    😴 WAITING
─────────────────────         ─────────────────────
Chờ 🔒 LOCK                  Chờ 🔔 SIGNAL
Không tự chọn                 Chủ động nhường CPU
synchronized block            wait() / join()
Thread khác giữ lock          Thread khác gọi notify()
```

</details>

---

Xong L1. Khi trả lời trơn tru 11 câu này không cần mở đáp án → chuyển sang **L2 Understand**.
