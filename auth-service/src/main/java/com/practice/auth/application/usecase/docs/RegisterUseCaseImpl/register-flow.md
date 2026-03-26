# Flow: Đăng ký tài khoản (có Email Verification)

Hai bước tách biệt:
- `POST /api/v1/auth/register` — tạo user + publish event → gửi email xác thực (async qua RabbitMQ)
- `GET  /api/v1/auth/verify-email?token=xxx` — kích hoạt tài khoản + phát JWT

---

## Bước 1 — POST /api/v1/auth/register

```
POST /api/v1/auth/register
  { username, email, password }
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AuthController                             │
│                                                                 │
│  @Valid kiểm tra:                                               │
│    - username: không trống, 3-50 ký tự                         │
│    - email: không trống, đúng định dạng email                   │
│    - password: không trống, tối thiểu 8 ký tự                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ authApiMapper.toRegisterCommand()
                           │ RegisterRequest → RegisterCommandDto
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   RegisterUseCaseImpl                           │
│                   (@Transactional → TX-1)                       │
│                                                                 │
│  ① ICreateUserPort.createUser(username, email, password)        │
│  ② rawToken = UUID.randomUUID()                                 │
│  ③ tokenHash = sha256(rawToken)                                 │
│  ④ EmailVerificationToken.create(userId, tokenHash, +24h)       │
│  ⑤ emailVerificationTokenRepository.save(token)                │
│  ⑥ eventPublisher.publishEvent(                                 │
│       new UserRegisteredEvent(email, rawToken))                 │
│     return void                                                 │
└───────┬─────────────────────────────────────────────────────────┘
        │
        │ ① ICreateUserPort.createUser(...)
        ▼
┌─────────────────────────────────────────────────────────────────┐
│               CreateUserServiceAdapter                          │
│           (auth-service infrastructure/external)                │
│                                                                 │
│  ICreateUserUseCase.execute(CreateUserCommandDto)               │
│                   ──── gọi sang user-service ────               │
└───────┬─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│               CreateUserUseCaseImplV3            [user-service] │
│                                                                 │
│  EmailVO.of(email)        → validate + chuẩn hóa               │
│  UsernameVO.of(username)  → validate                            │
│  passwordEncoder.encode() → BCrypt ~300ms (NGOÀI transaction)  │
│                                                                 │
│  TransactionTemplate (tham gia TX-1):                           │
│    ├─ userRepository.save(User.create(...))                     │
│    │       └─ User.create() → status = PENDING                  │
│    │       └─ saveAndFlush() → INSERT users                     │
│    │       └─ DataIntegrityViolationException                   │
│    │              └─ → UserConflictException (409)              │
│    └─ userProfileRepository.save(UserProfile.createEmpty(...))  │
│              └─ INSERT user_profiles                            │
│                                                                 │
│  return UserResponseDto { id, username, email, role, status }   │
└───────┬─────────────────────────────────────────────────────────┘
        │ UserResponseDto → CreatedUserResult { userId, email, role }
        │ (map tại CreateUserServiceAdapter)
        │
        └──────────────────────┐
                               │ quay lại RegisterUseCaseImpl
                               ▼
              ┌─────────────────────────────────────┐
              │  ② → ⑥ Verification token + event  │
              │                                     │
              │  rawToken  = UUID (128-bit)         │
              │  tokenHash = SHA-256(rawToken)      │
              │                                     │
              │  EmailVerificationToken.create(     │
              │    userId, tokenHash,               │
              │    now() + 24 hours                 │
              │  )                                  │
              │  → INSERT email_verification_tokens │
              │                                     │
              │  eventPublisher.publishEvent(       │
              │    UserRegisteredEvent(             │
              │      email, rawToken))              │
              │  → event queued in Spring context   │
              └────────────┬────────────────────────┘
                           │ void
                           ▼
              ┌────────────────────────────────────┐
              │  HTTP 202 ACCEPTED                 │
              │  {                                 │
              │    message: "Please check your     │
              │    email to verify your account"   │
              │  }                                 │
              └────────────────────────────────────┘
                           │
                    TX-1 COMMIT ✓
                           │
╔══════════════════════════▼═════════════════════════════════════╗
║  @TransactionalEventListener(AFTER_COMMIT)                     ║
║  UserRegisteredEventListener.handle()                          ║
║  (chỉ chạy sau khi TX-1 commit thành công,                     ║
║   không chạy nếu TX-1 rollback → không ghost messages)         ║
╚══════════════════════════╦═════════════════════════════════════╝
                           ║
                           ▼
┌──────────────────────────────────────────────────────────────┐
│  RabbitMQUserRegisteredPublisher.publish()                   │
│  rabbitTemplate.convertAndSend(                              │
│    exchange:   "auth.exchange"                               │
│    routingKey: "user.registered"                             │
│    body:       { email, rawToken } as JSON)                  │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                    🐰 RabbitMQ                                │
│  Exchange: auth.exchange (TOPIC)                             │
│      │                                                       │
│      │ routing: user.registered                              │
│      ▼                                                       │
│  Queue: notification.user.registered                         │
│      │                                                       │
│      │ (nếu fail 3 lần → exponential backoff 1s→2s→4s)      │
│      │ (sau 3 lần → DLX → DLQ)                              │
│      ▼                                                       │
│  DLQ: notification.user.registered.dlq                       │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│              worker-service :8083                            │
│  UserRegisteredNotificationConsumer                          │
│  @RabbitListener(queue = "notification.user.registered")     │
│                                                              │
│  ProcessedMessageTracker.tryMarkAsProcessed(rawToken)        │
│    ├─ false → duplicate → log warning → skip                 │
│    └─ true  → IWorkerEmailPort.sendVerificationEmail(        │
│                 email, rawToken)                             │
│                   ├─ WorkerMailSenderAdapter   (SMTP)        │
│                   │    link: /verify-email?token=<rawToken>  │
│                   └─ WorkerNoOpEmailAdapter    (dev/test)    │
│                        log.warn("[DEV] token: ...")          │
└──────────────────────────────────────────────────────────────┘
```

---

## Bước 2 — GET /api/v1/auth/verify-email?token=xxx

```
GET /api/v1/auth/verify-email?token=<rawToken>
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AuthController                             │
│  @RequestParam String token                                     │
└──────────────────────────┬──────────────────────────────────────┘
                           │ verifyEmailUseCase.execute(token)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   VerifyEmailUseCaseImpl                        │
│                   (@Transactional → TX-2)                       │
│                                                                 │
│  ① tokenHash = sha256(rawToken)                                 │
│  ② emailVerificationTokenRepository.findByTokenHash(hash)      │
│       └─ không tìm thấy → AuthDomainException (401)            │
│  ③ authDomainService.validateVerificationToken(token)           │
│       └─ đã dùng → AuthDomainException (401)                   │
│       └─ hết hạn → AuthDomainException (401)                   │
│  ④ token.markAsUsed()                                           │
│  ⑤ emailVerificationTokenRepository.save(token)                │
│  ⑥ activateUserPort.activate(userId)                           │
│  ⑦ rawRefreshToken = UUID.randomUUID()                          │
│  ⑧ refreshTokenHash = sha256(rawRefreshToken)                  │
│  ⑨ RefreshToken.create(userId, hash, now() + 30 days)          │
│  ⑩ refreshTokenRepository.save(refreshToken)                   │
│  ⑪ jwtPort.generateAccessToken(userId, "USER")                 │
│  ⑫ return AuthTokenDto                                          │
└───────┬─────────────────────────────────────────────────────────┘
        │
        │ ⑥ activateUserPort.activate(userId)
        ▼
┌─────────────────────────────────────────────────────────────────┐
│               ActivateUserServiceAdapter                        │
│           (auth-service infrastructure/external)                │
│                                                                 │
│  IActivateUserUseCase.execute(userId)                           │
│                   ──── gọi sang user-service ────               │
└───────┬─────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│               ActivateUserUseCaseImpl            [user-service] │
│                                                                 │
│  userRepository.findById(userId)                                │
│  user.activate() → status PENDING → ACTIVE                      │
│  userRepository.save(user) → UPDATE users SET status = 'ACTIVE' │
└─────────────────────────────────────────────────────────────────┘
        │
        └──────────────────────┐
                               │ quay lại VerifyEmailUseCaseImpl
                               ▼
              ┌────────────────────────────┐
              │  HTTP 200 OK               │
              │  {                         │
              │    accessToken:  "eyJ...", │
              │    refreshToken: "uuid",   │
              │    tokenType:    "Bearer", │
              │    expiresIn:    86400000  │
              │  }                         │
              └────────────────────────────┘
```

---

## Chuỗi biến đổi object

### POST /register

```
RegisterRequest              (api-portal, @Valid)
  → RegisterCommandDto       (AuthApiMapper.toRegisterCommand)
  → CreateUserCommandDto     (CreateUserServiceAdapter)
  → EmailVO / UsernameVO     (Value Objects, validate domain)
  → User (domain)            (User.create — status = PENDING)
  → UserJpaEntity            (INSERT users)
  → UserResponseDto          (user-service trả về)
  → CreatedUserResult        (CreateUserServiceAdapter map lại)
  → EmailVerificationToken   (EmailVerificationToken.create)
  → EmailVerificationTokenJpaEntity  (INSERT email_verification_tokens)
  → UserRegisteredEvent      (publishEvent — queued in Spring)
       [TX-1 COMMIT]
  → RabbitMQ message         (UserRegisteredEventListener → AFTER_COMMIT)
  → UserRegisteredMessage    (worker-service deserialize JSON)
  → SMTP email               (WorkerMailSenderAdapter)
  → HTTP 202 { message }
```

### GET /verify-email

```
?token=<rawToken>            (query param)
  → sha256(rawToken)         (tìm trong DB)
  → EmailVerificationToken   (load từ DB, validate, markAsUsed)
  → EmailVerificationTokenJpaEntity  (UPDATE used = true)
  → user.activate()          (UPDATE users SET status = ACTIVE)
  → RefreshToken (domain)    (RefreshToken.create)
  → RefreshTokenJpaEntity    (INSERT refresh_tokens)
  → JWT accessToken          (jwtPort.generateAccessToken)
  → AuthTokenDto
  → AuthTokenResponse        (AuthApiMapper.toResponse)
  → HTTP 200 { accessToken, refreshToken, ... }
```

---

## Transaction boundary

```
POST /register — TX-1:
  ├─ INSERT users                       (status = PENDING)
  ├─ INSERT user_profiles
  ├─ INSERT email_verification_tokens
  └─ publishEvent(UserRegisteredEvent)  ← chỉ queued, chưa gửi RabbitMQ
       │
       TX-1 COMMIT ✓
       │
       └─ @AFTER_COMMIT: gửi message lên RabbitMQ (async)
              └─ worker-service gửi email (async)

GET /verify-email — TX-2:
  ├─ UPDATE email_verification_tokens SET used = true
  ├─ UPDATE users SET status = ACTIVE
  └─ INSERT refresh_tokens

Nếu TX-1 rollback → user không tồn tại, RabbitMQ KHÔNG nhận message ✓
Nếu TX-2 rollback → token vẫn valid, user vẫn PENDING → có thể thử lại ✓
```

---

## Key Safety Guarantees

```
① @AFTER_COMMIT   → không gửi RabbitMQ nếu DB rollback (no ghost messages)
② ProcessedMessageTracker → không gửi email trùng lần 2 (idempotency)
③ Chỉ lưu tokenHash vào DB, rawToken chỉ đi qua event/email (security)
④ DLQ + retry     → không mất message nếu worker-service tạm thời down
```
