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
 ├── pom.xml                                ← parent: spring-boot-starter-parent 3.2.4
 │
 ├── 👤 user-service/                       ← Spring Boot library module (packaging: jar)
 │   ├── pom.xml
 │   └── src/main/java/com/practice/user/
 │       │
 │       ├── 💎 domain/                     ★ Pure Java — KHÔNG import framework
 │       │   ├── model/                     ← Entity (có ID, có lifecycle)
 │       │   ├── valueobject/               ← Immutable, không có ID
 │       │   ├── enums/                     ← Hằng số domain
 │       │   ├── exception/                 ← Business exception (không phải HTTP)
 │       │   ├── port/
 │       │   │   └── out/                   ← Output Port: domain nói "tôi CẦN gì"
 │       │   └── service/                   ← Domain Service (stateless, pure logic)
 │       │
 │       ├── ⚙️  application/               ★ Điều phối luồng — biết Spring, không biết JPA
 │       │   ├── dto/                       ← Command (write) / Response (read)
 │       │   ├── usecase/                   ← 1 class = 1 hành động của user
 │       │   └── port/
 │       │       ├── in/                    ← Input Port: app nói "tôi CÓ THỂ làm gì"
 │       │       └── out/                   ← Output Port mở rộng (ngoài domain/port)
 │       │
 │       └── 🔧 infrastructure/             ★ Chi tiết kỹ thuật — biết tất cả
 │           ├── persistence/
 │           │   ├── postgresql/            ← 🐘 PostgreSQL storage
 │           │   │   ├── entity/            ← JPA Entity (@Entity, @Table)
 │           │   │   ├── repository/        ← Spring Data JPA + native queries
 │           │   │   ├── mapper/            ← domain model ↔ JPA entity
 │           │   │   └── adapter/           ← implements domain/port/out
 │           │   ├── mongodb/               ← 🍃 MongoDB storage (khi cần)
 │           │   │   ├── document/          ← @Document
 │           │   │   ├── repository/        ← MongoRepository
 │           │   │   ├── mapper/            ← domain model ↔ Mongo document
 │           │   │   └── adapter/           ← implements domain/port/out
 │           │   └── redis/                 ← ⚡ Redis storage (khi cần)
 │           │       ├── mapper/            ← domain model ↔ cache DTO
 │           │       └── adapter/           ← implements cache port
 │           ├── external/                  ← 🌐 3rd party API adapters
 │           ├── messaging/
 │           │   ├── consumer/              ← 📨 Event consumer (RabbitMQ/Kafka)
 │           │   └── producer/              ← 📤 Event producer
 │           └── config/                    ← Spring @Configuration, @Bean wiring
 │
 ├── 🔐 auth-service/                       ← Spring Boot library module (packaging: jar)
 │   ├── pom.xml                            ← depends on user-service + JWT (jjwt 0.12.6)
 │   │                                         KHÔNG có: spring-boot-starter-web, springdoc
 │   │                                         KHÔNG có: spring-boot-maven-plugin (library, không repackage)
 │   └── src/main/java/com/practice/auth/
 │       │
 │       ├── 💎 domain/                     ★ Pure Java — KHÔNG import framework
 │       │   ├── model/                     ← RefreshToken (entity), UserCredential (read model/record)
 │       │   ├── enums/                     ← TokenTypeEnum
 │       │   ├── exception/                 ← AuthDomainException
 │       │   ├── port/
 │       │   │   └── out/                   ← IRefreshTokenRepository, IUserCredentialPort
 │       │   └── service/                   ← AuthDomainService (pure Java, no @Service)
 │       │
 │       ├── ⚙️  application/               ★ Điều phối luồng — biết Spring, không biết JPA/JWT impl
 │       │   ├── dto/                       ← LoginCommandDto, RefreshTokenCommandDto, AuthTokenDto
 │       │   ├── usecase/                   ← LoginUseCaseImpl, RefreshTokenUseCaseImpl, LogoutUseCaseImpl
 │       │   └── port/
 │       │       ├── in/                    ← ILoginUseCase, IRefreshTokenUseCase, ILogoutUseCase
 │       │       └── out/                   ← IJwtPort (interface — không biết jjwt trực tiếp)
 │       │
 │       └── 🔧 infrastructure/             ★ Chi tiết kỹ thuật — biết tất cả
 │           ├── persistence/postgresql/    ← 🐘 refresh_tokens table
 │           │   ├── entity/                ← RefreshTokenJpaEntity
 │           │   ├── repository/            ← IRefreshTokenJpaRepository + QueryRepository
 │           │   ├── mapper/                ← RefreshTokenPersistenceMapper
 │           │   └── adapter/               ← RefreshTokenPostgresqlAdapter
 │           ├── external/                  ← 🔗 Gọi sang service khác trong monolith
 │           │   └── UserCredentialServiceAdapter  ← implements IUserCredentialPort
 │           │         inject IGetUserCredentialUseCase (port/in của user-service)
 │           ├── security/                  ← JwtServiceImpl (implements IJwtPort, dùng jjwt)
 │           └── config/                    ← AuthDomainConfig (@Bean AuthDomainService)
 │                                             JwtProperties (@ConfigurationProperties("jwt"))
 │
 └── 🚪 api-portal/                         ← Spring Boot runnable app ★ Entry point duy nhất
     ├── pom.xml                            ← depends on user-service + auth-service
     └── src/main/java/com/practice/api/portal/
         ├── ApiPortalApplication.java      ← @SpringBootApplication
         │     scanBasePackages = "com.practice"  ← scan cả user + auth service
         ├── config/                        ← Spring config tập trung
         │   ├── SecurityConfig.java        ← 🛡️  BCrypt bean, HTTP security
         │   └── OpenApiConfig.java         ← 📖 Swagger / springdoc
         ├── exception/
         │   └── GlobalExceptionHandler.java ← 🚨 @RestControllerAdvice toàn bộ portal
         ├── 👤 user/                       ★ interfaces/rest cho user-service
         │   ├── controller/                ← @RestController
         │   ├── request/                   ← JSON input → validate → map to Command
         │   ├── response/                  ← 📦 HTTP contract riêng (KHÔNG dùng application DTO)
         │   └── mapper/                    ← Request → Command, AppDto → Response
         └── 🔐 auth/                       ★ interfaces/rest cho auth-service
             ├── controller/
             ├── request/
             ├── response/
             └── mapper/


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

 ┌──────────────────┐   port/in    ┌──────────────────────────────┐
 │  🔐 auth-service  │ ───────────▶ │  👤 user-service              │
 │  external/       │             │  IGetUserCredentialUseCase    │
 │  ServiceAdapter  │             │  (application/port/in/)       │
 └──────────────────┘             └──────────────────────────────┘
   implements IUserCredentialPort      ↑ explicit Maven dependency
   (domain/port/out của auth)          compiler bắt lỗi khi contract thay đổi

 💎 domain/  KHÔNG có mũi tên đi ra — chỉ import java.*

 🚫 api-portal controller KHÔNG import infrastructure/ trực tiếp
    → chỉ gọi qua application/port/in (UseCase interface)

 🚫 auth-service KHÔNG đọc users table trực tiếp
    → chỉ gọi qua IGetUserCredentialUseCase (port/in của user-service)


═══════════════════════════════════════════════════════════════════════════════════
📋 LAYER RULES — QUY TẮC TỪNG TẦNG
═══════════════════════════════════════════════════════════════════════════════════

 ┌──────────────────────────┬──────────────────────────────┬──────────────────────────────┐
 │  Layer                   │  ✅ Được import               │  ❌ KHÔNG được import       │
 ├──────────────────────────┼──────────────────────────────┼──────────────────────────────┤
 │  💎 domain/              │  java.* only                 │  Spring, JPA, infrastructure │
 │  ⚙️  application/        │  domain/                     │  JPA, SMTP, Kafka details    │
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
 → Mọi HTTP response phải là class thuộc api-portal/[service]/response/


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
  → Implementor: infrastructure/persistence/adapter/

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


═══════════════════════════════════════════════════════════════════════════════════
📦 MAVEN MODULES
═══════════════════════════════════════════════════════════════════════════════════

 ┌──────────────────────┬──────────────────────────────┬────────────────────────────────────┐
 │  Module              │  Inherits from               │  Packaging / Depends on            │
 ├──────────────────────┼──────────────────────────────┼────────────────────────────────────┤
 │  practice-services   │  spring-boot-starter-parent  │  pom                               │
 │  👤 user-service     │  practice-services           │  jar (library)                     │
 │  🔐 auth-service     │  practice-services           │  jar (library) + user-service      │
 │  🚪 api-portal       │  practice-services           │  jar (Spring Boot, runnable)       │
 │                      │                              │  + user-service + auth-service     │
 └──────────────────────┴──────────────────────────────┴────────────────────────────────────┘

 🚪 api-portal là module DUY NHẤT có @SpringBootApplication và chạy được.
 👤 user-service / 🔐 auth-service là library — không chạy độc lập.
 🔐 auth-service depends on 👤 user-service (gọi IGetUserCredentialUseCase qua port/in).


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
     → Chi tiết: xem why-no-application-service.md

 [4] 🚪 api-portal là entry point duy nhất VÀ là interfaces layer
     → 1 nơi duy nhất có Spring Security, BCrypt bean, @RestController
     → Tổ chức theo service: api-portal/user/, api-portal/auth/, ...
     → Services (user-service, auth-service) KHÔNG có interfaces/ layer
     → api-portal chỉ gọi application/port/in — không chứa business logic
     → Trade-off: tách service ra repo riêng thì phải mang theo api-portal/[service]/

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
     api-portal/user/controller/UserController.java
     api-portal/auth/controller/AuthController.java

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
```
