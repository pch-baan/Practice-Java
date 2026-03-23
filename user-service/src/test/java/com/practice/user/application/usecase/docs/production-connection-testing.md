# Cách test Connection Issues trong Production thực tế

## Tổng quan — 3 tầng testing

```
Tầng 1: Load Test        → phát hiện vấn đề TRƯỚC khi deploy
Tầng 2: Monitor Metrics  → quan sát TRONG KHI hệ thống chạy
Tầng 3: Chaos Engineering→ chủ ý GÂY LỖI để test độ chịu đựng
```

---

## Tầng 1 — Load Test trước khi deploy (Pre-production)

Dùng tool bắn request giả lập traffic thật:

```
k6 / Gatling / JMeter / Locust
         ↓
   App Server
         ↓
   PostgreSQL
```

### Ví dụ với k6 — tool phổ biến nhất hiện nay

```javascript
// script.js — giả lập 200 user đăng ký cùng lúc
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    spike_test: {
      executor: 'constant-vus',
      vus: 200,          // 200 user đồng thời
      duration: '30s',   // trong 30 giây
    }
  }
};

export default function () {
  const res = http.post('http://localhost:8080/api/v1/users', JSON.stringify({
    username: `user_${__VU}_${__ITER}`,
    email:    `user_${__VU}_${__ITER}@test.com`,
    password: 'Secret@123'
  }), { headers: { 'Content-Type': 'application/json' } });

  check(res, {
    'status 201': (r) => r.status === 201,
    'no timeout':  (r) => r.timings.duration < 5000,
  });
}
```

### k6 output khi có bug (BCrypt trong @Transactional)

```
✓ status 201       [ 47% ] ← chỉ 47% thành công → pool exhausted
✗ no timeout       [ 53% ] ← 53% bị timeout

http_req_duration: avg=4.8s  max=30s  ← request chờ rất lâu
http_req_failed:   53.2%              ← tỉ lệ lỗi cao
```

---

## Tầng 2 — Monitor metrics trong khi chạy (Real-time)

Spring Boot + HikariCP expose metrics sẵn qua Actuator + Micrometer.

### Config

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
```

### Các metrics quan trọng cần theo dõi

```
hikaricp.connections.active    → đang dùng
hikaricp.connections.idle      → đang rảnh
hikaricp.connections.pending   → đang chờ  ← DẤU HIỆU NGUY HIỂM
hikaricp.connections.timeout   → đã bị timeout
hikaricp.connections.acquire   → thời gian chờ lấy connection
```

### Dấu hiệu pool sắp kiệt

```
active  = 10  (= pool size, hết sạch)
pending = 50  (50 request đang chờ)  ← cảnh báo đỏ
timeout tăng                          ← người dùng đang thấy lỗi
```

### Stack monitoring phổ biến

```
Spring Boot Actuator
      ↓ expose /actuator/prometheus
Prometheus (scrape metrics mỗi 15s)
      ↓
Grafana (dashboard + alert)
      ↓
PagerDuty / Slack alert khi pending > threshold
```

---

## Tầng 3 — Chaos Engineering (Test độ chịu đựng)

Chủ ý **gây lỗi** để kiểm tra hệ thống phản ứng thế nào.

| Kịch bản | Cách thực hiện | Kiểm tra gì |
|---|---|---|
| DB chậm đột ngột | `tc netem delay 500ms` trên network | App có timeout đúng không |
| DB down hoàn toàn | `docker stop postgres` | App có fallback không |
| Pool cạn kiệt | k6 spike test | Error message có rõ ràng không |
| Connection bị kill | `pg_terminate_backend()` | HikariCP có reconnect không |

---

## Thực tế ở hầu hết công ty

```
Dev machine          →  Unit test + Integration test
Staging environment  →  Load test với k6/Gatling (trước khi lên production)
Production           →  Grafana dashboard + alert 24/7
                        Chaos test định kỳ (Netflix Chaos Monkey pattern)
```

---

## Áp dụng vào project này

Bước thực tế nhất theo thứ tự ưu tiên:

| # | Bước | Mục tiêu |
|---|---|---|
| 1 | Fix BCrypt ra ngoài `@Transactional` | Giải quyết root cause |
| 2 | Thêm Micrometer + Prometheus vào `api-portal` | Expose HikariCP metrics |
| 3 | Chạy k6 spike test | Verify con số 160 request đã tính toán |
| 4 | Setup Grafana dashboard | Monitor `hikaricp.connections.pending` |
