# Bloom's Taxonomy — Concurrency Learning Roadmap

## Framing: L1-L2 là gì?

**Không phải "vấn đề thực tế", không phải "problem to solve".**

Đây là **prerequisite knowledge** — kiến thức nền bắt buộc phải có trước khi hiểu được vấn đề thực tế.

---

## Phân loại đúng từng mục (L1-L2)

| Mục | Loại thực sự |
|---|---|
| Thread states (6 states, transitions) | **Concept** — cần biết để đọc thread dump, hiểu tool monitoring |
| `synchronized` cơ chế (monitor/lock) | **Concept** — giải thích *tại sao* BLOCKED xảy ra |
| `synchronized` method vs block | **Best practice** — biết để tránh bottleneck |
| `happens-before` | **Concept** — giải thích *tại sao* data race xảy ra |

---

## Vậy "vấn đề thực tế" nằm ở đâu?

Kiến thức L1-L2 là công cụ để **chẩn đoán** các vấn đề thực tế ở tầng cao hơn:

```
Vấn đề thực tế (L3-L4):
  → Race condition trong OrderService
  → Deadlock giữa 2 service
  → Performance bottleneck vì synchronized quá rộng
  → Stale data vì thiếu happens-before (visibility bug)

          ↑ cần hiểu

Prerequisite (L1-L2):  ← build nền ở đây
  → Thread states
  → synchronized cơ chế
  → happens-before
```

> **Tóm lại:** Bloom L1-L2 = học công cụ tư duy, chưa phải giải bài toán.
> Bạn đang build nền để sau đó tackle vấn đề thực tế ở L3-L4.

---

## Full Bloom Taxonomy — Concurrency

| Level | Tên | Loại câu hỏi | Ví dụ trong Concurrency |
|---|---|---|---|
| L1 | Remember | **What** — định nghĩa, liệt kê | "BLOCKED là gì?" / "6 thread states là gì?" |
| L2 | Understand | **Why + How it works** — giải thích cơ chế (môi trường kiểm soát) | "Tại sao thread bị BLOCKED?" / "synchronized hoạt động như thế nào?" |
| L3 | Apply | **How to fix** — áp dụng vào code/hệ thống thật | "Fix race condition trong OrderService này" / "Refactor synchronized method này thành block" |
| L4 | Analyze | **Why did it break** — tìm root cause từ triệu chứng | "Tại sao code này deadlock?" / "Thread dump này đang bị vấn đề gì?" |
| L5 | Evaluate | **Which is better** — so sánh trade-off, ra quyết định | "ReentrantLock vs synchronized — cái nào phù hợp bài toán này?" / "Approach A hay B ít risk hơn?" |
| L6 | Create | **How to design** — thiết kế từ đầu với constraints | "Design thread-safe cache cho hệ thống high-concurrency" / "Thiết kế rate limiter không dùng synchronized" |

### Ranh giới quan trọng

```
L2: "Tại sao synchronized method gây bottleneck?"
     → giải thích cơ chế, ví dụ demo tách biệt  ✅ L2

L3: "Fix cái OrderService này đang bị bottleneck"
     → áp dụng vào code thật, hệ thống thật     ✅ L3
```

> **L1-L2 = What/Why trong môi trường kiểm soát**
> Khi câu hỏi chuyển sang code thật, hệ thống thật → đã là L3+

---

## Roadmap học hiện tại

```
✅ L1-L2  prerequisite knowledge  (đang hoàn thành)
⬜ L3-L4  applied problems        (tiếp theo)
⬜ L5-L6  design & architecture   (senior level)
```
