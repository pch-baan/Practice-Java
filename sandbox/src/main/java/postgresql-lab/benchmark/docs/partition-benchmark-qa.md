# Q&A — Partition Benchmark

---

## Q: 263ms cũng nhanh mà? Tại sao lại coi là chậm?

263ms nghe có vẻ nhanh với con người — chớp mắt một cái là xong. Nhưng với database production thì khác hoàn toàn.

### Vấn đề không phải "1 query mất 263ms"

Vấn đề là **263ms × bao nhiêu lần/giây**:

```
worker-service poll PENDING orders mỗi 5 giây
→ 1 ngày = 17,280 lần query

263ms × 17,280 = 4,545 giây CPU/ngày
                = 1.26 giờ CPU chỉ để đọc PENDING
```

### So sánh tải thực tế

```
Scenario: 100 users đặt hàng cùng lúc
→ 100 query song song

Baseline:    100 × 263ms  → DB bắt đầu queue, latency tăng vọt
RANGE:       100 × 0.6ms  → xử lý xong trước khi queue hình thành
```

### Quy tắc ngón tay cái của DB

| Loại query | Target tốt |
|---|---|
| Index lookup (1 row) | < 1ms |
| Aggregation / scan nhỏ | < 10ms |
| Report / analytics | < 100ms |
| **263ms cho query thường xuyên** | ❌ quá chậm |

**Tóm lại:** 263ms với bạn = "ổn". 263ms với DB chạy production = **tích lũy thành bottleneck** khi traffic tăng. Benchmark này chạy trên local Docker — production với nhiều connection concurrent sẽ còn tệ hơn nhiều.
