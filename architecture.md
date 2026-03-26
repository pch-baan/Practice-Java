```
╔══════════════════════════════════════════════════════════════════════════════════╗
║          🏗️  PRACTICE SERVICES — KIẾN TRÚC TỔNG THỂ                              ║
╚══════════════════════════════════════════════════════════════════════════════════╝

 🧩 PATTERN: DDD + Layered Modular Monolith (inspired by Hexagonal / Ports & Adapters)
 ───────────────────────────────────────────────────────────────────────────────────
 ⚠️  NOTE: Không phải Hexagonal thuần túy — driving adapter (controller) nằm ở api-portal
           thay vì trong từng service. Services là pure business libraries, không chạy độc lập.


═══════════════════════════════════════════════════════════════════════════════════
📁 CẤU TRÚC PROJECT
═══════════════════════════════════════════════════════════════════════════════════

 practice.services/                         ← root (packaging: pom)
 ├── pom.xml                                ← parent: spring-boot-starter-parent 3.5.11
 │
 ├── 👤 user-service/                       ← Spring Boot library module (packaging: jar)
 │   ├── pom.xml
 │   └── src/main/java/com/practice/user/
 │       │
 │       ├── 💎 domain/                     ★ Pure Java — KHÔNG import framework
 │       │   ├── model/
 │       │   │   ├── User.java              ← Aggregate Root (id, username, email, passwordHash, role, status)
 │       │   │   └── UserProfile.java       ← Entity (fullName, displayName, gender, locale, timezone...)
 │       │   ├── valueobject/
 │       │   │   ├── EmailVO.java
 │       │   │   ├── UsernameVO.java
 │       │   │   └── PasswordHashVO.java
 │       │   ├── enums/
 │       │   │   ├── UserRoleEnum.java       ← USER, ADMIN
 │       │   │   ├── UserStatusEnum.java     ← PENDING, ACTIVE, INACTIVE
 │       │   │   └── GenderEnum.java
 │       │   ├── exception/
 │       │   │   ├── UserDomainException.java
 │       │   │   ├── UserNotFoundException.java
 │       │   │   └── UserConflictException.java
 │       │   ├── service/
 │       │   │   └── UserDomainService.java  ← stateless pure logic
 │       │   └── port/out/
 │       │       ├── IUserRepository.java
 │       │       └── IUserProfileRepository.java
 │       │
 │       ├── ⚙️  application/               ★ Điều phối luồng — biết Spring, không biết JPA
 │       │   ├── dto/
 │       │   │   ├── CreateUserCommandDto.java
 │       │   │   ├── UpdateUserProfileCommandDto.java
 │       │   │   ├── UserCredentialDto.java
 │       │   │   ├── UserResponseDto.java
 │       │   │   └── UserProfileResponseDto.java
 │       │   ├── port/in/
 │       │   │   ├── ICreateUserUseCase.java
 │       │   │   ├── IGetUserCredentialUseCase.java
 │       │   │   ├── IGetUserProfileUseCase.java
 │       │   │   ├── IUpdateUserProfileUseCase.java
 │       │   │   └── IActivateUserUseCase.java
 │       │   └── usecase/
 │       │       ├── CreateUserUseCaseImpl.java
 │       │       ├── GetUserCredentialUseCaseImpl.java
 │       │       ├── GetUserProfileUseCaseImpl.java
 │       │       ├── UpdateUserProfileUseCaseImpl.java
 │       │       └── ActivateUserUseCaseImpl.java
 │       │
 │       └── 🔧 infrastructure/             ★ Chi tiết kỹ thuật — biết tất cả
 │           ├── config/
 │           │   └── UserDomainConfig.java
 │           └── persistence/postgresql/
 │               ├── entity/
 │               │   ├── UserJpaEntity.java
 │               │   └── UserProfileJpaEntity.java
 │               ├── repository/
 │               │   ├── IUserJpaRepository.java
 │               │   ├── IUserProfileJpaRepository.java
 │               │   └── UserPostgresqlQueryRepository.java
 │               ├── mapper/
 │               │   ├── UserPersistenceMapper.java
 │               │   └── UserProfilePersistenceMapper.java
 │               └── adapter/
 │                   ├── UserPostgresqlAdapter.java      ← implements IUserRepository
 │                   └── UserProfilePostgresqlAdapter.java ← implements IUserProfileRepository
 │
 ├── 🔐 auth-service/                       ← Spring Boot library module (packaging: jar)
 │   ├── pom.xml                            ← depends on user-service + JWT (jjwt 0.12.6)
 │   │                                         KHÔNG có: spring-boot-starter-web, springdoc
 │   │                                         KHÔNG có: spring-boot-maven-plugin (library, không repackage)
 │   └── src/main/java/com/practice/auth/
 │       │
 │       ├── 💎 domain/                     ★ Pure Java — KHÔNG import framework
 │       │   ├── model/
 │       │   │   ├── UserCredential.java    ← Record (userId, username, passwordHash, role, status)
 │       │   │   ├── EmailVerificationToken.java  ← Entity (id, userId, tokenHash, expiresAt)
 │       │   │   └── RefreshToken.java      ← Entity (id, userId, tokenHash, expiresAt)
 │       │   ├── enums/
 │       │   │   └── TokenTypeEnum.java     ← ACCESS, REFRESH
 │       │   ├── exception/
 │       │   │   └── AuthDomainException.java
 │       │   ├── service/
 │       │   │   └── AuthDomainService.java
 │       │   └── port/out/
 │       │       ├── IEmailVerificationTokenRepository.java
 │       │       └── IRefreshTokenRepository.java
 │       │
 │       ├── ⚙️  application/               ★ Điều phối luồng — biết Spring, không biết JPA/JWT impl
 │       │   ├── dto/
 │       │   │   ├── RegisterCommandDto.java
 │       │   │   ├── LoginCommandDto.java
 │       │   │   ├── RefreshTokenCommandDto.java
 │       │   │   └── AuthTokenDto.java      ← accessToken, refreshToken, expiresIn
 │       │   ├── event/
 │       │   │   └── UserRegisteredEvent.java  ← Record (email, rawToken) — Spring domain event
 │       │   ├── port/in/
 │       │   │   ├── IRegisterUseCase.java
 │       │   │   ├── ILoginUseCase.java
 │       │   │   ├── ILogoutUseCase.java
 │       │   │   ├── IRefreshTokenUseCase.java
 │       │   │   ├── IVerifyEmailUseCase.java
 │       │   │   └── ICleanupExpiredTokensUseCase.java
 │       │   ├── port/out/
 │       │   │   ├── ICreateUserPort.java         ← gọi user-service: tạo user
 │       │   │   ├── IUserCredentialPort.java     ← gọi user-service: lấy credentials
 │       │   │   ├── IActivateUserPort.java       ← gọi user-service: activate user
 │       │   │   ├── IJwtPort.java                ← generate/validate JWT
 │       │   │   └── IUserRegisteredPublisherPort.java  ← publish event to RabbitMQ
 │       │   └── usecase/
 │       │       ├── RegisterUseCaseImpl.java      ← createUser → token → publishEvent
 │       │       ├── LoginUseCaseImpl.java
 │       │       ├── LogoutUseCaseImpl.java
 │       │       ├── RefreshTokenUseCaseImpl.java
 │       │       ├── VerifyEmailUseCaseImpl.java
 │       │       └── CleanupExpiredTokensUseCaseImpl.java
 │       │
 │       └── 🔧 infrastructure/             ★ Chi tiết kỹ thuật — biết tất cả
 │           ├── config/
 │           │   ├── AuthDomainConfig.java
 │           │   ├── JwtProperties.java     ← @ConfigurationProperties("jwt")
 │           │   └── RabbitMQConfig.java    ← Queue, Exchange, DLX declaration
 │           ├── external/                  ← 🔗 Gọi sang user-service trong monolith
 │           │   ├── CreateUserServiceAdapter.java    ← implements ICreateUserPort
 │           │   ├── UserCredentialServiceAdapter.java ← implements IUserCredentialPort
 │           │   └── ActivateUserServiceAdapter.java  ← implements IActivateUserPort
 │           ├── messaging/
 │           │   ├── internal/
 │           │   │   └── UserRegisteredEventListener.java  ← @TransactionalEventListener AFTER_COMMIT
 │           │   └── rabbitmq/
 │           │       └── RabbitMQUserRegisteredPublisher.java  ← implements IUserRegisteredPublisherPort
 │           ├── persistence/postgresql/
 │           │   ├── entity/
 │           │   │   ├── EmailVerificationTokenJpaEntity.java
 │           │   │   └── RefreshTokenJpaEntity.java
 │           │   ├── repository/
 │           │   │   ├── IEmailVerificationTokenJpaRepository.java
 │           │   │   ├── IRefreshTokenJpaRepository.java
 │           │   │   └── RefreshTokenPostgresqlQueryRepository.java
 │           │   ├── mapper/
 │           │   │   ├── EmailVerificationTokenPersistenceMapper.java
 │           │   │   └── RefreshTokenPersistenceMapper.java
 │           │   └── adapter/
 │           │       ├── EmailVerificationTokenPostgresqlAdapter.java
 │           │       └── RefreshTokenPostgresqlAdapter.java
 │           ├── scheduler/
 │           │   └── TokenCleanupScheduler.java    ← @Scheduled cleanup expired tokens
 │           └── security/
 │               └── JwtServiceImpl.java            ← implements IJwtPort (dùng jjwt)
 │
 ├── 🚪 api-portal/                         ← Spring Boot runnable app ★ Entry point duy nhất
 │   ├── pom.xml                            ← depends on user-service + auth-service
 │   └── src/main/java/com/practice/api/portal/
 │       ├── ApiPortalApplication.java      ← @SpringBootApplication
 │       │     scanBasePackages = "com.practice"  ← scan cả user + auth service
 │       ├── config/
 │       │   ├── SecurityConfig.java        ← 🛡️  BCrypt bean, JWT filter, HTTP security
 │       │   └── OpenApiConfig.java         ← 📖 Swagger / springdoc
 │       ├── exception/
 │       │   ├── GlobalExceptionHandler.java ← 🚨 @RestControllerAdvice — 400, 500
 │       │   ├── ErrorResponse.java         ← {code, message}
 │       │   ├── auth/
 │       │   │   └── AuthExceptionHandler.java  ← auth domain exceptions
 │       │   └── user/
 │       │       └── UserExceptionHandler.java  ← user domain exceptions
 │       └── v1/
 │           ├── 👤 user/                   ★ interfaces/rest cho user-service
 │           │   ├── controller/
 │           │   │   ├── UserController.java         ← GET /{id}, POST /
 │           │   │   └── UserProfileController.java  ← GET /{userId}, PUT /{userId}
 │           │   ├── request/
 │           │   │   ├── CreateUserRequest.java
 │           │   │   └── UpdateUserProfileRequest.java
 │           │   ├── response/
 │           │   │   ├── UserResponse.java
 │           │   │   ├── UserProfileResponse.java
 │           │   │   ├── UserRoleResponse.java
 │           │   │   └── UserStatusResponse.java
 │           │   └── mapper/
 │           │       ├── UserApiMapper.java
 │           │       └── UserProfileApiMapper.java
 │           └── 🔐 auth/                   ★ interfaces/rest cho auth-service
 │               ├── controller/
 │               │   └── AuthController.java  ← POST /register, /login, /refresh, /logout; GET /verify-email
 │               ├── request/
 │               │   ├── RegisterRequest.java
 │               │   ├── LoginRequest.java
 │               │   └── RefreshTokenRequest.java
 │               ├── response/
 │               │   ├── RegisterResponse.java
 │               │   └── AuthTokenResponse.java
 │               └── mapper/
 │                   └── AuthApiMapper.java
 │
 └── 📨 worker-service/                     ← Spring Boot runnable app (port 8083)
     ├── pom.xml                            ← NO spring-boot-starter-data-jpa, NO PostgreSQL
     └── src/main/java/com/practice/worker/
         ├── WorkerServiceApplication.java
         ├── application/
         │   └── port/
         │       └── IWorkerEmailPort.java  ← sendVerificationEmail(email, rawToken)
         ├── infrastructure/
         │   ├── config/
         │   │   └── WorkerRabbitConfig.java  ← mirror queue/exchange declaration
         │   ├── email/
         │   │   ├── WorkerMailSenderAdapter.java     ← implements IWorkerEmailPort, dùng SMTP
         │   │   ├── WorkerNoOpEmailAdapter.java      ← implements IWorkerEmailPort, dev fallback
         │   │   └── WorkerMailHostConfiguredCondition.java  ← @Conditional: MAIL_HOST set?
         │   └── idempotency/
         │       └── ProcessedMessageTracker.java     ← in-memory dedup, TTL 24h, @Scheduled cleanup
         └── listeners/auth/
             ├── UserRegisteredMessage.java           ← Record (email, rawToken)
             └── UserRegisteredNotificationConsumer.java  ← @RabbitListener


═══════════════════════════════════════════════════════════════════════════════════
🔗 DEPENDENCY RULE — QUY TẮC PHỤ THUỘC
═══════════════════════════════════════════════════════════════════════════════════

 ┌─────────────────────┐    ┌──────────────────┐    ┌────────────┐
 │  🚪 api-portal/     │───▶│  ⚙️  application/ │───▶│ 💎 domain/ │
 │  (interfaces layer) │    └──────────────────┘    └────────────┘
 └─────────────────────┘                                  ▲
                          ┌───────────────────────────────┘
                          │  🔧 infrastructure/
                          └───────────────────────────────

 💡 INTER-SERVICE DEPENDENCY (auth-service → user-service):

 ┌──────────────────┐   port/out   ┌──────────────────────────────┐
 │  🔐 auth-service  │ ──────────▶ │  👤 user-service              │
 │  external/       │             │  ICreateUserUseCase           │
 │  ServiceAdapters │             │  IGetUserCredentialUseCase    │
 │                  │             │  IActivateUserUseCase         │
 └──────────────────┘             │  (application/port/in/)       │
   implements ICreateUserPort     └──────────────────────────────┘
   implements IUserCredentialPort      ↑ explicit Maven dependency
   implements IActivateUserPort        compiler bắt lỗi khi contract thay đổi

 💡 EVENT FLOW (auth-service → RabbitMQ → worker-service):

 ┌──────────────────┐  AFTER_COMMIT  ┌──────────────────────────┐
 │  RegisterUseCase  │ ─────────────▶ │  UserRegisteredEvent     │
 │  (auth-service)  │               │  (Spring App Event)      │
 └──────────────────┘               └──────────────────────────┘
                                                │
                                   UserRegisteredEventListener
                                                │
                              RabbitMQUserRegisteredPublisher
                                                │
                                    ┌───────────▼───────────┐
                                    │  RabbitMQ             │
                                    │  auth.exchange (TOPIC)│
                                    │  → notification.user  │
                                    │    .registered        │
                                    └───────────┬───────────┘
                                                │
                              ┌─────────────────▼───────────────┐
                              │  📨 worker-service               │
                              │  UserRegisteredNotificationConsumer │
                              │  → ProcessedMessageTracker       │
                              │  → IWorkerEmailPort (SMTP/NoOp)  │
                              └──────────────────────────────────┘

 💎 domain/  KHÔNG có mũi tên đi ra — chỉ import java.*

 🚫 api-portal controller KHÔNG import infrastructure/ trực tiếp
    → chỉ gọi qua application/port/in (UseCase interface)

 🚫 auth-service KHÔNG đọc users table trực tiếp
    → chỉ gọi qua ICreateUserPort / IUserCredentialPort / IActivateUserPort


═══════════════════════════════════════════════════════════════════════════════════
📋 LAYER RULES — QUY TẮC TỪNG TẦNG
═══════════════════════════════════════════════════════════════════════════════════

 ┌──────────────────────────┬──────────────────────────────┬──────────────────────────────┐
 │  Layer                   │  ✅ Được import               │  ❌ KHÔNG được import       │
 ├──────────────────────────┼──────────────────────────────┼──────────────────────────────┤
 │  💎 domain/              │  java.* only                 │  Spring, JPA, infrastructure │
 │  ⚙️  application/        │  domain/                     │  JPA, SMTP, RabbitMQ details │
 │  🔧 infrastructure/      │  domain/, application/,      │  (không hạn chế)             │
 │                          │  Spring                      │                              │
 │  🚪 controller           │  application/port/in,        │  infrastructure/ trực tiếp,  │
 │                          │  Spring Web                  │  application/dto làm return  │
 │  🔄 mapper               │  application/dto (read-only) │  infrastructure/ trực tiếp   │
 │                          │  portal/request, portal/resp │                              │
 │  📥 request              │  jakarta.validation          │  application/, domain/       │
 │  📦 response             │  domain/enums (nếu cần)      │  application/dto             │
 └──────────────────────────┴──────────────────────────────┴──────────────────────────────┘

 🔒 HTTP CONTRACT RULE — BẮT BUỘC:
 → Controller KHÔNG được dùng application/dto làm return type của @RequestMapping method
 → application/dto chỉ được dùng nội bộ trong mapper (nhận vào, map ra response)
 → Mọi HTTP response phải là class thuộc api-portal/v1/[service]/response/


═══════════════════════════════════════════════════════════════════════════════════
🔌 PORT / IN vs PORT / OUT — Ý NGHĨA
═══════════════════════════════════════════════════════════════════════════════════

  🟢 port/in  (Input Port — Driving Port)
  → App nói: "Đây là những gì tôi CÓ THỂ LÀM"
  → Caller: api-portal/controller
  → Implementor: application/usecase/

  🔵 port/out (Output Port — Driven Port)
  → Domain / App nói: "Đây là những gì tôi CẦN từ bên ngoài"
  → Caller: domain/service/ + application/usecase/
  → Implementor: infrastructure/persistence/adapter/ hoặc infrastructure/external/

  🌊 REQUEST FLOW (đầy đủ):

       🚪 api-portal/                 ⚙️  application/              🔧 infrastructure/
       ──────────────────────────     ──────────────────────         ──────────────────
       📥 request/  controller/       port/in   usecase/             port/out  adapter/
       Request ──[mapper]──► Command ──in──►  UseCase  ──out──►  Port  ──►  Adapter
                                                  │                              │
                                             DomainService               🐘 DB / ⚡ Cache
                                                  │
                             📦 Response ◄─[mapper]─ AppDto ◄──────────────────┘
                             (api-portal)           (application/dto — 🔒 internal only)

  📮 REGISTER FLOW (event-driven):

       AuthController
            │ POST /api/v1/auth/register
            ▼
       RegisterUseCaseImpl
            ├── ICreateUserPort.createUser()        → user-service (PENDING status)
            ├── EmailVerificationToken.create()     → generate + hash token
            ├── IEmailVerificationTokenRepository.save()  → DB
            └── ApplicationEventPublisher.publishEvent(UserRegisteredEvent)
                         │
                         │ @TransactionalEventListener(AFTER_COMMIT)
                         ▼
                UserRegisteredEventListener
                         │
                         ▼
                RabbitMQUserRegisteredPublisher
                         │ auth.exchange / routing key: user.registered
                         ▼
                    RabbitMQ Queue
                         │ notification.user.registered
                         ▼
            UserRegisteredNotificationConsumer  (worker-service)
                    ├── ProcessedMessageTracker.tryMarkAsProcessed()  ← idempotency
                    └── IWorkerEmailPort.sendVerificationEmail()       ← SMTP / NoOp


═══════════════════════════════════════════════════════════════════════════════════
📮 EVENT-DRIVEN MESSAGING — RABBITMQ
═══════════════════════════════════════════════════════════════════════════════════

  ┌─────────────────────────────────────────────────────────────────────────┐
  │  Exchange / Queue / Routing Key                                         │
  ├─────────────────┬───────────────────────────────┬──────────────────────┤
  │  Exchange       │  auth.exchange                 │  type: TOPIC         │
  │  Queue          │  notification.user.registered  │                      │
  │  Routing Key    │  user.registered               │                      │
  │  DLX            │  worker.dlx                    │  type: DIRECT        │
  │  DLQ            │  notification.user.registered.dlq │                   │
  │  DLQ Routing Key│  notification.user.registered.failed │                │
  └─────────────────┴───────────────────────────────┴──────────────────────┘

  🔁 Retry Policy:
     → Max 3 attempts, exponential backoff: 1s → 2s → 4s (max interval 10s)
     → Sau 3 lần thất bại: RejectAndDontRequeue → route to DLQ via DLX

  🛡️  Idempotency (ProcessedMessageTracker):
     → Key: rawToken (email verification token)
     → TTL: 24h (khớp với email token expiry)
     → Cleanup: @Scheduled mỗi giờ
     → Storage: in-memory ConcurrentHashMap (không cần DB)

  ⚡ Transactional Guarantee:
     → @TransactionalEventListener(phase = AFTER_COMMIT)
     → RabbitMQ chỉ nhận message nếu DB transaction SUCCESS
     → Không có ghost messages khi rollback


═══════════════════════════════════════════════════════════════════════════════════
📦 MAVEN MODULES
═══════════════════════════════════════════════════════════════════════════════════

 ┌──────────────────────┬──────────────────────────────┬────────────────────────────────────┐
 │  Module              │  Inherits from               │  Packaging / Depends on            │
 ├──────────────────────┼──────────────────────────────┼────────────────────────────────────┤
 │  practice-services   │  spring-boot-starter-parent  │  pom                               │
 │  👤 user-service     │  practice-services           │  jar (library)                     │
 │  🔐 auth-service     │  practice-services           │  jar (library) + user-service      │
 │  🚪 api-portal       │  practice-services           │  jar (Spring Boot, runnable :8080) │
 │                      │                              │  + user-service + auth-service     │
 │  📨 worker-service   │  practice-services           │  jar (Spring Boot, runnable :8083) │
 │                      │                              │  NO JPA, NO PostgreSQL             │
 └──────────────────────┴──────────────────────────────┴────────────────────────────────────┘

 🚪 api-portal là module DUY NHẤT expose REST API, chứa Spring Security + Swagger.
 📨 worker-service là module DUY NHẤT xử lý async messaging, chứa SMTP adapter.
 👤 user-service / 🔐 auth-service là library — không chạy độc lập.
 🔐 auth-service depends on 👤 user-service (gọi qua port/in — không đọc DB trực tiếp).


═══════════════════════════════════════════════════════════════════════════════════
💡 DESIGN DECISIONS — LÝ DO THIẾT KẾ
═══════════════════════════════════════════════════════════════════════════════════

 [1] 🚫 KHÔNG có commons module
     → Mỗi service hoàn toàn độc lập
     → Tách ra repo riêng: chỉ cần di chuyển folder + đổi <parent> thành
       spring-boot-starter-parent

 [2] 📁 Package-based layers (không phải Maven sub-module per layer)
     → Cấu trúc đơn giản hơn, navigate dễ hơn
     → Trade-off: enforce layer isolation bằng convention, không phải compiler

 [3] ✅ application/usecase/ thay vì application/service/
     → Tên UseCase thể hiện rõ từng hành động (SRP)
     → Dễ trace: 1 interface port/in ↔ 1 implementation
     → Phù hợp với Hexagonal: port/in là "use case" của hệ thống

 [4] 🚪 api-portal là entry point duy nhất VÀ là interfaces layer
     → 1 nơi duy nhất có Spring Security, BCrypt bean, @RestController
     → Tổ chức theo phiên bản API: api-portal/v1/user/, api-portal/v1/auth/, ...
     → Services (user-service, auth-service) KHÔNG có interfaces/ layer
     → api-portal chỉ gọi application/port/in — không chứa business logic
     → Trade-off: tách service ra repo riêng thì phải mang theo api-portal/v1/[service]/

     ┌─────────────────────────────────────────────────────────────────────┐
     │  🔍 PHÂN TÍCH: TẠI SAO KHÔNG ĐỂ CONTROLLER TRONG TỪNG SERVICE?      │
     │  (Option A vs Option B)                                             │
     └─────────────────────────────────────────────────────────────────────┘

     Option A — Controller trong mỗi service (interfaces/ per service)
     ─────────────────────────────────────────────────────────────────
     user-service/interfaces/rest/controller/UserController.java
     auth-service/interfaces/rest/controller/AuthController.java

     Option B — Controller tập trung trong api-portal  ✅ CHỌN
     ─────────────────────────────────────────────────────────────────
     api-portal/v1/user/controller/UserController.java
     api-portal/v1/auth/controller/AuthController.java

     SO SÁNH CHI TIẾT:

     [A1] 📦 Dependency của service
          ❌ Option A: user-service phải thêm spring-boot-starter-web
                      → library jar mang theo HTTP layer dù không chạy độc lập
                      → vi phạm nguyên tắc "service là pure business library"
          ✅ Option B: user-service chỉ cần spring-boot-starter (không có web)
                      → service không biết HTTP tồn tại

     [A2] 🔗 Cross-cutting concerns (error format, response wrapper, logging)
          ❌ Option A: GlobalExceptionHandler phải duplicate ở mỗi service
                      HOẶC tạo commons module → vi phạm [1] (không có commons)
          ✅ Option B: 1 GlobalExceptionHandler duy nhất trong api-portal
                      → tất cả API có cùng error format, response shape

     [A3] 🛡️  Spring Security
          ❌ Option A: @PreAuthorize, SecurityFilterChain phải config ở mỗi service
                      → mỗi service cần spring-boot-starter-security
                      → BCrypt bean phân tán, khó kiểm soát
          ✅ Option B: SecurityConfig chỉ ở api-portal
                      → 1 nơi duy nhất quyết định authn/authz cho toàn hệ thống

     [A4] 💎 Độ thuần khiết của service
          ❌ Option A: service biết HTTP (HttpStatus, ResponseEntity, @Valid...)
                      → domain boundary bị pha loãng
          ✅ Option B: service chỉ biết business logic
                      → có thể test domain/application hoàn toàn độc lập với HTTP

     [A5] 🤔 Khi nào Option A TỐT HƠN
          → Mỗi service chạy độc lập trên port riêng (true microservices)
          → Mỗi service có thể deploy riêng lẻ mà không cần api-portal
          → Ví dụ:
               user-service  ── :8081  (có controller riêng, chạy được)
               auth-service  ── :8082  (có controller riêng, chạy được)
               api-gateway   ── :8080  (chỉ route, không có business)
          → Project này KHÔNG theo mô hình đó → Option A không phù hợp

     🏁 KẾT LUẬN: Option B tối ưu hơn cho modular monolith (1 JVM, 1 deployable).
                  Option A chỉ có ý nghĩa khi tách thành microservices thật sự.

 [5] 🔗 auth-service gọi user-service qua port/in (không đọc DB trực tiếp)
     → auth-service cần thông tin credentials từ users table
     → Option bị loại: auth-service tự tạo UserCredentialJpaEntity đọc thẳng users table
         ✗ Schema coupling ẩn — compiler không bắt được khi user-service đổi schema
         ✗ Runtime mới bị lỗi: "column hashed_password does not exist"
     → Option được chọn: auth-service inject IGetUserCredentialUseCase (port/in của user-service)
         ✓ Explicit Maven dependency — ai đọc code cũng thấy ngay
         ✓ Contract thay đổi → compile error → fail fast
         ✓ infrastructure/external/ dùng đúng mục đích theo architecture này
         ✓ Tách microservice sau: chỉ đổi impl từ in-process → HTTP, không sửa auth-service
     → Trade-off: auth-service phụ thuộc user-service module (explicit dep)

 [6] 📮 worker-service tách biệt, không có DB access
     → worker-service chỉ làm 1 việc: consume message → gửi email
     → KHÔNG có Spring Data JPA, KHÔNG có PostgreSQL dependency
     → Idempotency qua in-memory ProcessedMessageTracker (không cần bảng DB)
         ✓ TTL 24h khớp với email token expiry — tự nhiên hết hạn cùng lúc
         ✓ Đủ cho single-instance deployment (modular monolith)
         ✓ Nếu scale horizontal: cần migrate sang Redis/DB-backed tracker
     → @TransactionalEventListener AFTER_COMMIT đảm bảo không gửi message khi DB rollback
     → DLQ đảm bảo không mất message khi SMTP down
```
