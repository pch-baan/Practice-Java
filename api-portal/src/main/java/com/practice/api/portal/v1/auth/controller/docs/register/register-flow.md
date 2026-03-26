```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                     FLOW: POST /api/v1/auth/register                           ║
╚══════════════════════════════════════════════════════════════════════════════════╝

┌─────────┐          ┌──────────────────────────────────────────────────────────┐
│  CLIENT │          │                     api-portal :8080                     │
└────┬────┘          └──────────────────────────────────────────────────────────┘
     │                              │
     │  POST /api/v1/auth/register  │
     │  { email, password, ... }    │
     │ ─────────────────────────────►
     │                              │
     │                    ┌─────────▼─────────┐
     │                    │  AuthController   │
     │                    │  .register()      │
     │                    └─────────┬─────────┘
     │                              │  mapToCommand(RegisterRequest)
     │                              ▼
     │              ┌────────────────────────────────────┐
     │              │      RegisterUseCaseImpl           │   ◄── @Transactional
     │              │      .execute(command)             │
     │              └──────────────┬─────────────────────┘
     │                             │
     │                    ┌────────▼────────┐
     │                    │  ① Create User  │
     │                    │  ICreateUserPort│  ──► user status = PENDING
     │                    └────────┬────────┘
     │                             │
     │                    ┌────────▼────────────────────┐
     │                    │  ② Generate Token           │
     │                    │  rawToken = UUID.random()   │
     │                    │  tokenHash = sha256(raw)    │
     │                    │  expiresAt = now + 24h      │
     │                    └────────┬────────────────────┘
     │                             │
     │                    ┌────────▼──────────────────┐
     │                    │  ③ Save tokenHash to DB   │  ──► PostgreSQL
     │                    │  (NOT rawToken!)           │
     │                    └────────┬──────────────────┘
     │                             │
     │                    ┌────────▼──────────────────────────────┐
     │                    │  ④ Publish UserRegisteredEvent        │
     │                    │  eventPublisher.publishEvent(         │
     │                    │    new UserRegisteredEvent(           │
     │                    │      email, rawToken))                │
     │                    └────────┬──────────────────────────────┘
     │                             │
     │  HTTP 202 ACCEPTED          │  ◄── Transaction COMMIT ──►
     │  "Check your email..."      │
     ◄─────────────────────────────│
     │                             │
     │              ╔══════════════▼═══════════════════════════════╗
     │              ║  @TransactionalEventListener(AFTER_COMMIT)   ║
     │              ║  UserRegisteredEventListener.handle()        ║
     │              ╚══════════════╦═══════════════════════════════╝
     │                             ║  (chỉ chạy nếu DB commit OK)
     │                             ║  không chạy nếu rollback
     │                             ▼
     │              ┌──────────────────────────────────────────────┐
     │              │  RabbitMQUserRegisteredPublisher.publish()   │
     │              │  rabbitTemplate.convertAndSend(              │
     │              │    exchange:   "auth.exchange"               │
     │              │    routingKey: "user.registered"             │
     │              │    body:       { email, rawToken } as JSON)  │
     │              └──────────────────────┬───────────────────────┘
     │                                     │
     │              ┌──────────────────────▼───────────────────────┐
     │              │              🐰 RabbitMQ                      │
     │              │  Exchange: auth.exchange (TOPIC)              │
     │              │      │                                        │
     │              │      │ routing: user.registered               │
     │              │      ▼                                        │
     │              │  Queue: notification.user.registered          │
     │              │      │                                        │
     │              │      │ (nếu fail 3 lần → DLX → DLQ)          │
     │              │      ▼                                        │
     │              │  DLQ: notification.user.registered.dlq        │
     │              └──────────────────────┬───────────────────────┘
     │                                     │
     │              ┌──────────────────────▼───────────────────────┐
     │              │         worker-service :8083                  │
     │              │  UserRegisteredNotificationConsumer           │
     │              │  @RabbitListener(queue = "notification...")   │
     │              └──────────────────────┬───────────────────────┘
     │                                     │
     │                         ┌───────────▼───────────────┐
     │                         │  ProcessedMessageTracker  │
     │                         │  .tryMarkAsProcessed(     │
     │                         │    rawToken)              │
     │                         └───────────┬───────────────┘
     │                                     │
     │               ┌─────────────────────┴──────────────────────┐
     │               │                                            │
     │          return false                                 return true
     │       (duplicate message)                         (first time seen)
     │               │                                            │
     │       ┌───────▼────────┐                    ┌─────────────▼────────────┐
     │       │  ⚠ SKIP        │                    │  IWorkerEmailPort        │
     │       │  log warning   │                    │  .sendVerificationEmail( │
     │       └────────────────┘                    │    email, rawToken)       │
     │                                             └──────────┬───────────────┘
     │                                                        │
     │                                    ┌───────────────────┴────────────────┐
     │                                    │                                    │
     │                          SMTP configured?                        dev/test?
     │                                    │                                    │
     │                    ┌───────────────▼──────────┐          ┌─────────────▼──────────┐
     │                    │  WorkerMailSenderAdapter  │          │  WorkerNoOpEmailAdapter│
     │                    │  Send real email via SMTP │          │  log.warn("[DEV] ...")  │
     │                    │  Link:                    │          │  (không gửi thật)      │
     │                    │  /verify-email?token=...  │          └────────────────────────┘
     │                    └───────────────────────────┘
     │
     ▼
  ✅ DONE


╔══════════════════════════════════════════════════════════╗
║                  TÓM TẮT CÁC LỚP THAM GIA              ║
╠══════════════════════════════════════════════════════════╣
║  api-portal  │ AuthController                           ║
╠══════════════╪══════════════════════════════════════════╣
║  auth-service│ RegisterUseCaseImpl      (@Transactional) ║
║              │ UserRegisteredEventListener (@AFTER_COMMIT)║
║              │ RabbitMQUserRegisteredPublisher           ║
╠══════════════╪══════════════════════════════════════════╣
║  RabbitMQ    │ auth.exchange → notification.user.regist. ║
╠══════════════╪══════════════════════════════════════════╣
║  worker-svc  │ UserRegisteredNotificationConsumer        ║
║              │ ProcessedMessageTracker  (idempotency)    ║
║              │ WorkerMailSenderAdapter / NoOpAdapter     ║
╚══════════════╧══════════════════════════════════════════╝

  KEY SAFETY GUARANTEES:
  ① @AFTER_COMMIT  → không gửi RabbitMQ nếu DB rollback
  ② ProcessedMessageTracker → không gửi email trùng lần 2
  ③ Chỉ lưu tokenHash vào DB, rawToken chỉ đi qua event/email
```
