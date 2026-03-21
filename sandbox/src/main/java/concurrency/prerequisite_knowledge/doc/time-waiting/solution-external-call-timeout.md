# Solution: Timeout cho External Calls

## Vấn đề

Mọi lời gọi ra ngoài JVM (HTTP API, DB, message queue...) đều có thể chậm hoặc treo.
Nếu không có timeout → thread rơi vào **WAITING** vĩnh viễn → thread leak → service chết dần.

## Solution: Luôn có timeout ở 2 tầng

### Tầng 1 — HTTP Client timeout (khuyên dùng trước)

```java
// RestTemplate
factory.setConnectTimeout(3000);  // chờ kết nối tối đa 3s
factory.setReadTimeout(5000);     // chờ response tối đa 5s

// WebClient
WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(
        HttpClient.create().responseTimeout(Duration.ofSeconds(5))
    ));
```

→ Thread vào `TIMED_WAITING` ngay tại socket, không cần `CompletableFuture`.

---

### Tầng 2 — `future.get(N, unit)` (safety net)

```java
// Dùng khi fan-out: gọi nhiều API song song rồi gom kết quả
CompletableFuture<String> f1 = callServiceA();
CompletableFuture<String> f2 = callServiceB();

String a = f1.get(5, SECONDS); // TIMED_WAITING
String b = f2.get(5, SECONDS); // TIMED_WAITING
```

---

## Rule thực tế

| Trường hợp | Solution |
|---|---|
| Gọi 1 API đơn giản | Timeout ở HTTP Client là đủ |
| Gọi song song nhiều API | HTTP Client timeout + `future.get(N)` |
| Gọi internal service (cùng JVM) | Không cần — overhead không đáng kể |

---

## Kết luận

```
WAITING       = future.get()           → không deadline → nguy hiểm ❌
TIMED_WAITING = future.get(5, SECONDS) → có deadline    → an toàn   ✅
```

**Mọi lời gọi ra ngoài JVM đều phải có timeout** — chỉ là set ở tầng nào phụ thuộc vào cách gọi.

## Demo

- `WaitingCompletableFutureDemo.java` — so sánh WAITING vs TIMED_WAITING với external API
