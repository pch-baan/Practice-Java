# Gap Analysis — Bloom L1-L2

| Kiến thức yêu cầu | Trạng thái | Nằm ở đâu |
|---|---|---|
| Thread states — 6 states, transition | ✅ Đủ | `1-thread-states.md` |
| Thread states — 80/20 thực tế | ✅ Đủ | `1-thread-states.md` |
| `synchronized` method vs block — so sánh, scope | ✅ Đủ | `doc/sync/2-synchronized-method-vs-block.md` |
| `synchronized` — cơ chế hoạt động (monitor/lock) | ✅ Đủ | `doc/sync/1-synchronized-mechanism.md` |
| happens-before — khái niệm, tại sao cần | ✅ Đủ | `doc/happens-before/1-happens-before.md` |
| happens-before — các rule cụ thể | ✅ Đủ | `doc/happens-before/1-happens-before.md` |

---

## Cụ thể những gì đang thiếu

### 1. `synchronized` cơ chế

Chưa có file nào giải thích:

- Monitor/intrinsic lock là gì
- Thread `BLOCKED` xảy ra như thế nào khi tranh lock

### 2. `synchronized` method vs block

Q11 trong `active-recall.md` chỉ nói "dùng block hẹp hơn" nhưng không giải thích:

- Method lock trên `this` (hoặc `Class` nếu `static`)
- Block có thể lock trên bất kỳ object nào
- Tại sao scope nhỏ hơn → throughput cao hơn

### 3. happens-before

Hoàn toàn chưa có:

- Visibility problem là gì (thread đọc giá trị cũ)
- happens-before relationship trong JMM
- Rule: `synchronized` và `volatile` đảm bảo happens-before
- Không có happens-before → data race
