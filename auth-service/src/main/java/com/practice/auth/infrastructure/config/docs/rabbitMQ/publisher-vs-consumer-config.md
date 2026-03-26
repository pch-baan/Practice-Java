# RabbitMQConfig vs WorkerRabbitConfig — Publisher vs Consumer

## Tổng quan

| | `RabbitMQConfig` (auth-service) | `WorkerRabbitConfig` (worker-service) |
|---|---|---|
| **Vai trò** | Publisher | Consumer |
| **Khai báo Exchange** | ✅ `auth.exchange` (TopicExchange) | ✅ `auth.exchange` + `worker.dlx` (DLX) |
| **Khai báo Queue** | ❌ | ✅ `notification.user.registered` + DLQ |
| **Binding** | ❌ | ✅ Queue → Exchange, DLQ → DLX |
| **RabbitTemplate** | ✅ (với ConfirmCallback) | ❌ |
| **ListenerContainerFactory** | ❌ | ✅ (với Retry + DLQ) |

## Luồng tổng thể

```
auth-service (api-portal)                         worker-service
──────────────────────────                         ──────────────
RabbitMQConfig                                     WorkerRabbitConfig
  ├─ TopicExchange (auth.exchange)    ←── cùng exchange ───► TopicExchange (auth.exchange)
  └─ RabbitTemplate                                          ├─ DirectExchange (worker.dlx)
       └─ ConfirmCallback                                    ├─ Queue (notification.user.registered)
                                                             ├─ Queue (notification.user.registered.dlq)
                                                             ├─ Binding: Queue → auth.exchange
                                                             ├─ Binding: DLQ → worker.dlx
                                                             └─ SimpleRabbitListenerContainerFactory
                                                                  └─ Retry (3x, exponential backoff)
```

## Tại sao Consumer declare Queue, không phải Producer?

Convention trong RabbitMQ: **consumer chịu trách nhiệm declare queue**.

```
Producer (auth-service)     Broker          Consumer (worker-service)
       │                      │                      │
       │  declare Exchange ──►│                      │
       │                      │◄── declare Queue ────│
       │                      │◄── bind Queue ───────│
       │                      │                      │
       │── publish ──────────►│──── deliver ────────►│
```

**Lý do:** Producer không được biết ai đang lắng nghe hay queue tên gì.
Nếu `auth-service` declare queue → bị coupled với consumer → vi phạm decoupling.

Producer chỉ cần biết: **exchange name + routing key**.
Consumer tự quyết định: **queue name + binding**.

## Publisher Confirms (Async)

`RabbitMQConfig` cấu hình `ConfirmCallback` trên `RabbitTemplate`:

```
publish msg ──► broker nhận ──► ACK/NACK ──► callback (thread khác)
                                               ack=false → log ERROR
```

Yêu cầu bật trong `application.yml`:
```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated   # async ACK/NACK from broker
```

| Chế độ | An toàn | Block thread | Throughput |
|---|---|---|---|
| Fire & Forget (default) | ❌ | Không | Cao nhất |
| Sync Confirm | ✅ | Có | Thấp |
| **Async Confirm (đang dùng)** | ✅ | Không | Cao |

`WorkerRabbitConfig` **không cần** publisher confirms vì worker-service chỉ consume, không publish.
