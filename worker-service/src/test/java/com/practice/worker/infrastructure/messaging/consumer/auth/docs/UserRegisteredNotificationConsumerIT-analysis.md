# Integration Test Analysis: UserRegisteredNotificationConsumerIT

## 1. Container Setup

```java
@Container
@ServiceConnection
static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:4-management");
```

**What happens:**
- `@Testcontainers` scans for `@Container` fields before Spring context starts
- Docker pulls `rabbitmq:4-management`, starts container on a random port (e.g. `localhost:53830`)
- `@ServiceConnection` overrides `spring.rabbitmq.host` and `spring.rabbitmq.port` automatically

**Maps to `application.yml` lines 12–16:**
```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}   # overridden by @ServiceConnection
    port: ${RABBITMQ_PORT:5672}        # overridden by @ServiceConnection
```

`static` = one container shared across all tests in the class — not restarted per test.

---

## 2. MockitoBean replaces the real email adapter

```java
@MockitoBean
IWorkerEmailPort emailPort;
```

**Resolution chain in ApplicationContext:**

1. `WorkerMailHostConfiguredCondition.matches()` checks `spring.mail.host`
   — `application.yml` line 19: `host: ${MAIL_HOST:}` → empty → condition = `false`
   — `WorkerMailSenderAdapter` is **not created**

2. `WorkerNoOpEmailAdapter` is created (`@Component`, no condition)

3. `@MockitoBean` finds existing `IWorkerEmailPort` bean → **replaces** it with Mockito mock

4. `UserRegisteredNotificationConsumer` receives the mock via constructor injection (line 17–18):
```java
private final IWorkerEmailPort emailPort;       // receives mock
private final ProcessedMessageTracker tracker;  // real bean — idempotency logic runs for real
```

---

## 3. RabbitTemplate message converter setup

```java
@Autowired
@Qualifier("defaultMessageConverter")
Jackson2JsonMessageConverter converter;

@BeforeEach
void setUp() {
    rabbitTemplate.setMessageConverter(converter);
}
```

**Why this is required:**

`RabbitTemplate` auto-configured by Spring Boot uses `SimpleMessageConverter` by default.
Without this setup, the message is serialized as `application/octet-stream` (Java bytes),
which the consumer's `Jackson2JsonMessageConverter` cannot deserialize → `MessageConversionException`.

After setting the converter:
- Publisher (test): `UserRegisteredMessage` → `{"email":"...","rawToken":"..."}` (JSON)
- Consumer (`defaultListenerFactory`): JSON → `UserRegisteredMessage` record ✅

**Maps to `WorkerRabbitConfig.java` lines 25–28:**
```java
@Bean("defaultMessageConverter")
public Jackson2JsonMessageConverter defaultMessageConverter() {
    return new Jackson2JsonMessageConverter(); // same bean injected into the test
}
```

And lines 106–114 — `defaultListenerFactory` sets this converter on the consumer side:
```java
factory.setMessageConverter(converter);
```

Both sides use the same converter → JSON format is consistent end-to-end.

---

## 4. Happy path test

```java
rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message);

await().atMost(5, SECONDS).untilAsserted(() ->
    verify(emailPort).sendVerificationEmail("user@test.com", token)
);
```

**Step-by-step flow:**

```
rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message)
    │
    │  Exchange: auth.exchange  (WorkerRabbitConfig line 34–37)
    │  Routing key: user.registered
    ▼
RabbitMQ container routes message:
    │  authTopicExchange (TOPIC) matches "user.registered"
    │  → delivers to queue "notification.user.registered"
    │  (binding declared at WorkerRabbitConfig lines 75–81)
    ▼
@RabbitListener(queues = "${worker.consumer.user-registered.queue}")
    │  UserRegisteredNotificationConsumer.java line 22
    │  containerFactory = "defaultListenerFactory"
    ▼
handleUserRegistered(UserRegisteredMessage message)   [line 24]
    ├─ tracker.tryMarkAsProcessed(rawToken) → true (first time)
    └─ emailPort.sendVerificationEmail("user@test.com", token)
              ↑ Mockito mock records the call — no real email sent

await().atMost(5, SECONDS)
    │  Consumer runs on a separate thread (SimpleMessageListenerContainer pool)
    │  Awaitility polls every 100ms until verify() passes or timeout
    ▼
verify(emailPort).sendVerificationEmail("user@test.com", token) ✅
```

---

## 5. Idempotency test

```java
// Publish first — wait for it to be processed
rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message);
await().atMost(5, SECONDS).untilAsserted(() ->
    verify(emailPort, atLeastOnce()).sendVerificationEmail(anyString(), eq(token))
);

// Publish duplicate (same token)
rabbitTemplate.convertAndSend("auth.exchange", "user.registered", message);
Thread.sleep(500);

// Email must have been sent exactly once
verify(emailPort, times(1)).sendVerificationEmail(anyString(), eq(token));
```

**How `ProcessedMessageTracker` blocks the duplicate:**

```
── Message 1 ──────────────────────────────────────────────────────

tracker.tryMarkAsProcessed("abc-uuid-token")
    └── processed.putIfAbsent("abc-uuid-token", now) == null
                              map is empty → returns null
                              null == null → TRUE

emailPort.sendVerificationEmail(...) called ✅

await(atLeastOnce()) passes


── Message 2 (same token) ─────────────────────────────────────────

tracker.tryMarkAsProcessed("abc-uuid-token")
    └── processed.putIfAbsent("abc-uuid-token", now)
                              key exists → returns existing timestamp (not null)
                              not null == null → FALSE

log.warn("Duplicate message — email already sent to ..., skipping")
return; ← emailPort is NOT called

Thread.sleep(500) → allows consumer to finish processing the duplicate

verify(times(1)) ✅ emailPort was called exactly once
```

**Maps to `ProcessedMessageTracker.java` lines 36–38:**
```java
public boolean tryMarkAsProcessed(String key) {
    return processed.putIfAbsent(key, Instant.now().toEpochMilli()) == null;
}
```

**Why `UUID.randomUUID()` per test:**

`ProcessedMessageTracker` is a Spring singleton backed by a `ConcurrentHashMap`.
The ApplicationContext is reused across tests (Spring caches it by default).
If both tests used the same fixed token, test 2 would see it already in the map
and skip the email → `verify()` would fail.
`UUID.randomUUID()` guarantees a fresh key per test with no cross-test contamination.

---

## 6. Full pipeline diagram

```
[Test]
  │ convertAndSend("auth.exchange", "user.registered", JSON)
  ▼
[RabbitMQ Container]─────────────────────────────────────┐
  │ authTopicExchange (TOPIC)                            │
  │ routing key match → notification.user.registered     │
  │ exhausted retries → DLX → notification...dlq         │
  ▼                                                      │
[WorkerRabbitConfig — defaultListenerFactory]            │
  │ Jackson2JsonMessageConverter                         │
  │ retry: 3 attempts, backoff 1s → 2s → 4s             │
  │ RejectAndDontRequeueRecoverer ───────────────────────┘
  ▼
[UserRegisteredNotificationConsumer.handleUserRegistered()]
  │ tracker.tryMarkAsProcessed(rawToken)
  │   true  → emailPort.sendVerificationEmail()  ← mock records call
  │   false → log.warn + return (duplicate skipped)
  ▼
[Test]
  await().untilAsserted(() -> verify(emailPort)...)
```

---

## 7. Key decisions summary

| Decision | Reason |
|---|---|
| `static` container | One container shared across all tests — faster startup |
| `@ServiceConnection` | Auto-overrides `spring.rabbitmq.*` — no hardcoded ports |
| `@MockitoBean IWorkerEmailPort` | Replaces NoOp adapter — allows `verify()`, no real SMTP |
| `ProcessedMessageTracker` not mocked | Idempotency logic must run for real to be tested |
| `rabbitTemplate.setMessageConverter(converter)` | Publisher and consumer must use the same JSON format |
| `UUID.randomUUID()` per test | Prevents cross-test contamination in the in-memory tracker |
| `await().atMost(5s)` | Consumer is async — test thread cannot verify immediately |
| `Thread.sleep(500)` in idempotency test | Grace period for the duplicate message to be consumed |
