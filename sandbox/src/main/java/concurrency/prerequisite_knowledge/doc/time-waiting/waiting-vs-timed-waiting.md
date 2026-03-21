# WAITING vs TIMED_WAITING

Chỉ 1 điểm khác nhau cốt lõi:

```
WAITING          = chờ không có deadline → cần người khác "gọi dậy"
TIMED_WAITING    = chờ có deadline       → tự dậy khi hết giờ
```

---

## Ai gọi dậy được

| | WAITING | TIMED_WAITING |
|---|---|---|
| Hết timeout | ❌ không có | ✅ tự dậy |
| `notify()` / `complete()` | ✅ | ✅ |
| `interrupt()` | ✅ | ✅ |

---

## Vấn đề thực tế

**WAITING** — nếu future không bao giờ complete (service crash, bug logic...)
→ thread treo **vĩnh viễn**, không có cách thoát tự nhiên
→ thread leak, memory leak theo thời gian.

**TIMED_WAITING** — dù service crash
→ thread vẫn tự dậy sau N giây
→ ném `TimeoutException` → xử lý được.

---

## Rule trong production

```java
// ❌ NGUY HIỂM
future.get();

// ✅ AN TOÀN
future.get(5, TimeUnit.SECONDS);
```

Bất kỳ chỗ nào dùng `get()` không có timeout trong Spring Boot đều là time bomb.

---

## Demo

- `WaitingCompletableFutureDemo.java` — WAITING: `future.get()` treo vĩnh viễn
- `TimedWaitingExternalApiDemo.java`  — TIMED_WAITING: `future.get(5, SECONDS)` tự thoát
