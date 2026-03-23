# Hỏi & Đáp — Internal Messaging (Spring ApplicationEvent)

---

## Câu hỏi 1: Tại sao `UserRegisteredEvent` nằm ở `application/event/` ?

**Trả lời:**

`UserRegisteredEvent` chứa `rawToken` — đây là **application concern**, không phải domain concept:

```java
public record UserRegisteredEvent(String email, String rawToken) {}
//                                               ↑
//                              Domain không biết đến raw token
//                              Domain chỉ biết tokenHash
```

Domain chỉ quan tâm "user đã đăng ký" — không quan tâm email gửi như thế nào, token raw là gì.

Event này dùng để **điều phối luồng nghiệp vụ** trong application layer:

```
RegisterUseCaseImpl (application)
    → publish UserRegisteredEvent
        → UserRegisteredEventListener (infrastructure) nhận
            → gửi email
```

Nó đóng vai trò như một **DTO trung gian** giữa application layer và infrastructure layer.

**Phân biệt Domain Event vs Application Event:**

| | Domain Event | Application Event |
|---|---|---|
| Ví dụ | `UserActivatedDomainEvent` | `UserRegisteredEvent` |
| Nằm ở | `domain/event/` | `application/event/` |
| Chứa gì | Dữ liệu thuần domain | Dữ liệu cần cho orchestration |
| Ai publish | Domain model | UseCase |

---

## Câu hỏi 2: `UserRegisteredEventListener` là một dạng gửi event message nội bộ đúng không? Khác gì với RabbitMQ?

**Trả lời:**

Đúng — cả hai đều là **event-driven pattern**, nhưng khác nhau hoàn toàn về phạm vi và khả năng.

**Spring ApplicationEvent = bảng tin nội bộ trong 1 JVM**

```
RegisterUseCaseImpl
    │ publishEvent()
    ▼
[In-Memory Event Bus] ← chỉ tồn tại trong RAM, cùng process
    │
    ▼
UserRegisteredEventListener.onUserRegistered()
```

**RabbitMQ = bưu điện giữa các process/server**

```
RegisterUseCaseImpl
    │ publish message
    ▼
[RabbitMQ Broker] ← process riêng, có disk persistence
    │
    ▼
EmailConsumerService (có thể ở server khác)
```

**So sánh trực tiếp:**

| | Spring ApplicationEvent | RabbitMQ |
|---|---|---|
| Phạm vi | Cùng JVM | Nhiều process, nhiều server |
| Persistence | Không — mất khi app crash | Có — message lưu trên disk |
| Retry | Không có | Có (DLQ, retry policy) |
| App restart | Message mất | Message vẫn còn trong queue |
| Overhead | Zero (in-memory) | Network + serialization |
| Setup | Không cần gì thêm | Cần RabbitMQ server |
| Ordering | Đảm bảo | Không đảm bảo |

**Điểm giống nhau duy nhất:** Cả hai đều giải quyết bài toán **decoupling** — publisher không cần biết ai đang lắng nghe.

**Hậu quả khi app crash giữa chừng:**

```
App crash lúc 3:00:30 sau khi DB commit nhưng trước khi gửi email:

Spring ApplicationEvent:
  → Event mất → email không bao giờ được gửi

RabbitMQ:
  → Message vẫn còn trong queue
  → App restart xong → consumer tiếp tục xử lý → email được gửi
```

> Spring ApplicationEvent đủ dùng cho monolith. RabbitMQ cần thiết khi cần **durability** hoặc **cross-service communication**.

---

## Câu hỏi 3: Di chuyển vào `infrastructure/messaging/internal/` có hợp lý không?

**Trả lời:** Hợp lý — thậm chí còn tốt hơn `infrastructure/event/`.

```
infrastructure/messaging/
    internal/   ← Spring ApplicationEvent (in-process)
    external/   ← RabbitMQ, Kafka (tương lai)
```

`internal/` nói rõ ý định ngay từ tên thư mục — ai đọc code cũng hiểu đây là **in-process messaging**, phân biệt rõ với external broker sau này nếu thêm vào.

**So sánh các lựa chọn:**

| Vị trí | Nhận xét |
|---|---|
| `infrastructure/event/` | Ổn, nhưng không nói lên được messaging intent |
| `infrastructure/messaging/internal/` ✅ | Rõ ràng nhất — phân tầng messaging theo scope |
| `infrastructure/messaging/` (flat) | Ổn nếu không có kế hoạch thêm external |
