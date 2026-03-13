```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  🧱  CREATE USER FEATURE — THỨ TỰ TẠO FILE & LUỒNG DỮ LIỆU  🧱                   ║
╚══════════════════════════════════════════════════════════════════════════════════╝

  💡 NGUYÊN TẮC: Tạo từ TRONG ra NGOÀI (domain trước, interfaces sau)

       🏠 Domain      →     ⚙️ Application      →     🔌 Infrastructure      →     🌐 API
     (pure Java)         (Spring @Service)          (JPA + PostgreSQL)          (Controller)

 ─────────────────────────────────────────────────────────────────────────────────


┌──────────────────────────────────────────────────────────────────────────────────┐
│  🏗️  BƯỚC 1 — DOMAIN LAYER                                                       │
│       Pure Java · Không import framework · Trái tim của hệ thống                 │
└──────────────────────────────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  user-service/domain/                                                       │
 │                                                                             │
 │  ├── 📦 model/                                                              │
 │  │   └── User.java           ⭐ Tạo đầu tiên — trái tim của app             │
 │  │       ├── static create(String username, String email, String pwdHash)   │
 │  │       │     ← factory method, nội bộ tự wrap String → VO                 │
 │  │       ├── static reconstruct(UUID, String...) ← load từ DB               │
 │  │       ├── activate() / deactivate() ← business methods với guard clause  │
 │  │       ├── updateEmail() / updateUsername() / changePassword()            │
 │  │       ├── isActive()                                                     │
 │  │       └── equals/hashCode by ID   ← DDD rule                             │
 │  │                                                                          │
 │  ├── 💎 valueobject/          Bọc primitive — mang business rule bên trong  │
 │  │   ├── EmailVO.java         ← validate format, lowercase, trim            │
 │  │   ├── UsernameVO.java      ← validate not blank, trim                    │
 │  │   └── PasswordHashVO.java  ← wrap BCrypt hash string                     │
 │  │       (mỗi VO: private constructor + static of() + getValue())           │
 │  │                                                                          │
 │  ├── 🏷️  enums/                                                             │
 │  │   ├── UserStatusEnum.java  ← ACTIVE | INACTIVE                           │
 │  │   └── UserRoleEnum.java    ← USER | ADMIN                                │
 │  │                                                                          │
 │  ├── 🚨 exception/                                                          │
 │  │   └── UserDomainException.java  ← business exception (not HTTP!)         │
 │  │                                                                          │
 │  ├── 🧠 service/              Business logic span nhiều entity/VO           │
 │  │   └── UserDomainService.java  ← KHÔNG có @Service annotation             │
 │  │       └── validateUniqueConstraints(EmailVO, UsernameVO)                 │
 │  │             → existsByEmail?    throw UserDomainException                │
 │  │             → existsByUsername? throw UserDomainException                │
 │  │                                                                          │
 │  └── 🔌 port/out/             Cổng ra — domain nói "tôi cần gì"             │
 │      └── IUserRepository.java ← interface (prefix I = Interface)            │
 │          ├── save(User): User                                               │
 │          ├── findById(UUID): Optional<User>                                 │
 │          ├── findByUsername(UsernameVO): Optional<User>  ← auth support     │
 │          ├── existsByEmail(EmailVO)                                         │
 │          └── existsByUsername(UsernameVO)                                   │
 └─────────────────────────────────────────────────────────────────────────────┘

                               │
                               │  📥  application/ import domain/
                               ▼

┌──────────────────────────────────────────────────────────────────────────────────┐
│  ⚙️  BƯỚC 2 — APPLICATION LAYER                                                  │
│       Biết Spring @Service · Không biết JPA / SMTP                               │
└──────────────────────────────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  user-service/application/                                                  │
 │                                                                             │
 │  ├── 🚪 port/in/              Input Port — "hệ thống CÓ THỂ LÀM gì"         │
 │  │   └── ICreateUserUseCase.java                                            │
 │  │       └── execute(CreateUserCommandDto): UserResponseDto                 │
 │  │                                                                          │
 │  ├── 📋 dto/                                                                │
 │  │   ├── CreateUserCommandDto.java  ← input: username, email, password      │
 │  │   ├── UserResponseDto.java       ← output: id, username, email, role...  │
 │  │   │   └── static from(User)        ← convert domain → response           │
 │  │   └── UserCredentialDto.java     ← record: userId, username,             │
 │  │         passwordHash, role, status  ← auth-service dùng để verify login  │
 │  │                                                                          │
 │  ├── 🚪 port/in/                                                            │
 │  │   ├── ICreateUserUseCase.java                                            │
 │  │   │   └── execute(CreateUserCommandDto): UserResponseDto                 │
 │  │   └── IGetUserCredentialUseCase.java  ← auth-service gọi vào đây         │
 │  │       ├── findByUsername(String): Optional<UserCredentialDto>            │
 │  │       └── findByUserId(UUID): Optional<UserCredentialDto>                │
 │  │                                                                          │
 │  └── 🎯 usecase/                                                            │
 │      ├── CreateUserUseCaseImpl.java  ← @Service, implements ICreateUserUseCase│
 │      └── GetUserCredentialUseCaseImpl.java  ← @Service (auth support)       │
 │          └── execute(command):                                              │
 │              ① EmailVO.of() / UsernameVO.of()  ← wrap vào VO (validate)    │
 │              ② userDomainService                                           │
 │                   .validateUniqueConstraints(email, username)               │
 │              ③ passwordEncoder.encode(password)  ← BCrypt (Spring Security)│
 │              ④ User.create(String, String, String) ← gọi domain factory    │
 │              ⑤ userRepository.save(user)         ← gọi qua IUserRepository │
 │              ⑥ return UserResponseDto.from(savedUser)                      │
 └─────────────────────────────────────────────────────────────────────────────┘

                               │
                               │  🔧  infrastructure/ implements domain ports
                               ▼

┌──────────────────────────────────────────────────────────────────────────────────┐
│  🔌 BƯỚC 3 — INFRASTRUCTURE LAYER                                                │
│       Biết tất cả: JPA · Spring · PostgreSQL                                     │
└──────────────────────────────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  user-service/infrastructure/                                               │
 │                                                                             │
 │  ├── ⚙️  config/                                                            │
 │  │   └── UserDomainConfig.java      ← @Configuration                        │
 │  │       ├── @Bean userDomainService(IUserRepository)                       │
 │  │       │     → new UserDomainService(userRepository)                      │
 │  │       │     ⭐ Vì domain service không có @Service (pure Java)           │
 │  │       └── @Bean passwordEncoder()                                        │
 │  │             → new BCryptPasswordEncoder()                                │
 │  │             ⭐ Khai báo ở đây để user-service & auth-service tự đủ      │
 │  │               (auth-service depend on user-service → nhận transitively)  │
 │  │                                                                          │
 │  └── 🗄️  persistence/postgresql/                                            │
 │      ├── 🧩 entity/                                                         │
 │      │   └── UserJpaEntity.java     ← @Entity @Table("users")               │
 │      │       ├── @Id UUID id                                                │
 │      │       ├── String username (unique), String email (unique)            │
 │      │       ├── String passwordHash                                        │
 │      │       ├── @Enumerated UserRoleEnum role                              │
 │      │       ├── @Enumerated UserStatusEnum status                          │
 │      │       ├── LocalDateTime createdAt (updatable=false)                  │
 │      │       └── LocalDateTime updatedAt                                    │
 │      │                                                                      │
 │      ├── 🗃️  repository/                                                    │
 │      │   ├── IUserJpaRepository.java  ← extends JpaRepository (Spring Data) │
 │      │   │   ├── findByEmail(String): Optional<UserJpaEntity>               │
 │      │   │   ├── findByUsername(String): Optional<UserJpaEntity>  ← auth    │
 │      │   │   ├── existsByEmail(String)                                      │
 │      │   │   └── existsByUsername(String)                                   │
 │      │   │                                                                  │
 │      │   └── UserPostgresqlQueryRepository.java  ← @Repository              │
 │      │       ← Native PostgreSQL queries (pg_trgm, JSONB, etc.)             │
 │      │       ← Tách khỏi IUserJpaRepository để giữ JPQL-only ở đó           │
 │      │       (hiện tại: placeholder, chưa có query thật)                    │
 │      │                                                                      │
 │      ├── 🔄 mapper/                                                         │
 │      │   └── UserPersistenceMapper.java  ← @Component, convert 2 chiều      │
 │      │       ├── toJpaEntity(User):                                         │
 │      │       │     .username(user.getUsername().getValue())  ← unwrap VO    │
 │      │       │     .email(user.getEmail().getValue())        ← unwrap VO    │
 │      │       │     .passwordHash(user.getPasswordHash().getValue()) ← unwrap│
 │      │       └── toDomain(entity) → User.reconstruct(UUID, String, ...)     │
 │      │                                                                      │
 │      └── 🔌 adapter/                                                        │
 │          └── UserPostgresqlAdapter.java  ← @Repository                      │
 │              └── implements IUserRepository  ← cầu nối domain ↔ JPA         │
 │                  ├── save(User): entity = mapper.toJpaEntity(user)          │
 │                  │               saved  = jpaRepo.save(entity)              │
 │                  │               return mapper.toDomain(saved)              │
 │                  ├── findById(UUID): jpaRepo.findById().map(mapper::toDomain)│
 │                  ├── findByUsername(UsernameVO u)  ← auth support            │
 │                  │     → jpaRepo.findByUsername(u.getValue()).map(...)       │
 │                  ├── existsByEmail(EmailVO e)                               │
 │                  │     → jpaRepo.existsByEmail(e.getValue())  ← unwrap VO   │
 │                  └── existsByUsername(UsernameVO u)                         │
 │                        → jpaRepo.existsByUsername(u.getValue()) ← unwrap VO │
 └─────────────────────────────────────────────────────────────────────────────┘

                               │
                               │  🌐  api-portal depends on user-service as Maven module
                               ▼

┌──────────────────────────────────────────────────────────────────────────────────┐
│  🌐 BƯỚC 4 — API LAYER  (module riêng: api-portal)                                │
│       Entry point duy nhất của toàn hệ thống                                     │
└──────────────────────────────────────────────────────────────────────────────────┘

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  api-portal/                                                                │
 │                                                                             │
 │  ├── 🚀 ApiPortalApplication.java    ← @SpringBootApplication               │
 │  │       scanBasePackages = "com.practice"  ← scan cả user + auth service   │
 │  │       @EnableJpaRepositories(basePackages = "com.practice")              │
 │  │       @EntityScan(basePackages = "com.practice")                         │
 │  │                                                                          │
 │  ├── ⚙️  config/                                                            │
 │  │   ├── SecurityConfig.java    ← csrf disable, STATELESS, permitAll        │
 │  │   │     (PasswordEncoder KHÔNG khai báo ở đây — nằm ở UserDomainConfig)  │
 │  │   └── OpenApiConfig.java     ← Swagger/OpenAPI configuration             │
 │  │                                                                          │
 │  ├── 🛡️  exception/                                                         │
 │  │   └── GlobalExceptionHandler.java  ← @RestControllerAdvice               │
 │  │       ├── UserDomainException         → 409 CONFLICT                     │
 │  │       └── MethodArgumentNotValidException → 400 BAD REQUEST              │
 │  │           (trả về ErrorResponse { status, message, timestamp })          │
 │  │                                                                          │
 │  └── 👤 user/                                                               │
 │      ├── 📥 request/                                                        │
 │      │   └── CreateUserRequest.java  ← @NotBlank @Email @Size               │
 │      │         validate HTTP input (Bean Validation — tầng 1)               │
 │      │         username: @Size(min=3, max=50)                               │
 │      │         password: @Size(min=8)                                       │
 │      │                                                                      │
 │      ├── 📤 response/                                                       │
 │      │   └── UserResponse.java  ← HTTP response contract (riêng biệt)       │
 │      │         ≠ UserResponseDto (application DTO)                          │
 │      │         Tách để HTTP contract độc lập với application layer          │
 │      │                                                                      │
 │      ├── 🔄 mapper/                                                         │
 │      │   └── UserApiMapper.java  ← @Component, map 2 chiều                  │
 │      │       ├── toCommand(CreateUserRequest) → CreateUserCommandDto        │
 │      │       └── toResponse(UserResponseDto)  → UserResponse                │
 │      │                                                                      │
 │      └── 🎮 controller/                                                     │
 │          └── UserController.java  ← @RestController @RequestMapping         │
 │              ├── inject ICreateUserUseCase (port/in interface)              │
 │              │     Spring tự inject CreateUserUseCaseImpl                   │
 │              ├── inject UserApiMapper                                       │
 │              └── POST /api/users → ResponseEntity<UserResponse> 201         │
 └─────────────────────────────────────────────────────────────────────────────┘


╔══════════════════════════════════════════════════════════════════════════════════╗
║  🛡️  VALIDATE 2 TẦNG — TẠI SAO CẦN CẢ HAI?                                       ║
╚══════════════════════════════════════════════════════════════════════════════════╝

            🌐 HTTP Request đến
                    │
                    ▼
  ┌─────────────────────────────────────────┐
  │  🛡️  TẦNG 1 — CreateUserRequest         │  [api-portal]
  │       @NotBlank, @Email, @Size          │
  │                                         │
  │  ✅ Chặn HTTP rác trước khi vào app     │
  │  ✅ Chỉ bảo vệ REST endpoint này        │
  │  ❌ Lỗi → GlobalExceptionHandler        │
  │           → 400 BAD REQUEST             │
  └──────────────────┬──────────────────────┘
                     │ nếu pass
                     ▼
  ┌─────────────────────────────────────────┐
  │  🔐 TẦNG 2 — Value Object               │  [user-service/domain]
  │       EmailVO.of(), UsernameVO.of()     │
  │                                         │
  │  ✅ Business rule nằm trong domain      │
  │  ✅ Không thể bypass dù data đến từ đâu │
  │     (REST, MQ, scheduler, test...)      │
  │  ❌ Lỗi → UserDomainException           │
  └─────────────────────────────────────────┘

  ⚠️  Ví dụ bypass tầng 1:
       User.create("john", "not-an-email", "pass")   ← không qua Request
       → VO chặn ở đây → UserDomainException ✅


╔══════════════════════════════════════════════════════════════════════════════════╗
║  🚪 PORT/IN vs PORT/OUT — Ý NGHĨA & VỊ TRÍ                                       ║
╚══════════════════════════════════════════════════════════════════════════════════╝

   🚪 port/in  (Input Port — Driving Port)      → nằm ở: application/port/in/
   ├── "Đây là những gì hệ thống CÓ THỂ LÀM"
   ├── Caller:      api-portal (Controller)
   ├── Implementor: application layer (UseCase)
   └── Vì: input port phụ thuộc vào application DTO (CreateUserCommandDto, UserResponseDto)

   🔌 port/out (Output Port — Driven Port)       → nằm ở: domain/port/out/
   ├── "Đây là những gì domain CẦN từ bên ngoài"
   ├── Caller:      domain service + application layer
   ├── Implementor: infrastructure layer (Adapter)
   └── Vì: output port chỉ dùng domain types (User, EmailVO, UsernameVO)

  ┌──────────────┐   port/in    ┌────────────────────────┐   port/out  ┌───────────────────────┐
  │  🌐 api-portal│ ──────────► │  ⚙️  ICreateUserUseCase  │ ──────────►│  🔌 UserPostgresql    │
  │  Controller  │             │  (application/port/in/) │             │     Adapter            │
  └──────────────┘             └───────────┬────────────┘              │  (infrastructure/)     │
                                           │                           └────────────────────────┘
                                           ▼
                               ┌─────────────────────┐
                               │  🧠 UserDomainService │
                               │   (domain/service/)  │
                               └─────────────────────┘


╔══════════════════════════════════════════════════════════════════════════════════╗
║  🧠 DOMAIN SERVICE vs APPLICATION SERVICE — PHÂN BIỆT                            ║
╚══════════════════════════════════════════════════════════════════════════════════╝

  ┌───────────────────────────────────────────────────────────────┐
  │  🧠 UserDomainService         (domain/service/)               │
  ├───────────────────────────────────────────────────────────────┤
  │  ✅ Business rule: "email/username phải unique"               │
  │  ✅ Dùng port/out để kiểm tra                                 │
  │  ❌ Không biết HTTP, BCrypt, transaction                      │
  │  ❌ KHÔNG có @Service                                         │
  │     → được tạo qua @Bean trong UserDomainConfig               │
  │     (domain layer không được phụ thuộc vào Spring)            │
  └───────────────────────────────────────────────────────────────┘

  ┌───────────────────────────────────────────────────────────────┐
  │  ⚙️  CreateUserUseCaseImpl     (application/usecase/)         │
  ├───────────────────────────────────────────────────────────────┤
  │  ✅ Điều phối luồng: gọi domain service + BCrypt + save       │
  │  ✅ Biết @Transactional, PasswordEncoder                      │
  │  ❌ Không chứa business rule                                  │
  └───────────────────────────────────────────────────────────────┘


╔══════════════════════════════════════════════════════════════════════════════════╗
║  🌊 LUỒNG DỮ LIỆU KHI GỌI  POST /api/users                                       ║
╚══════════════════════════════════════════════════════════════════════════════════╝

  👤 CLIENT
    │
    │  POST /api/users
    │  {
    │    "username": "john",
    │    "email":    "john@gmail.com",
    │    "password": "secret123"
    │  }
    │
    ▼
 ╔═══════════════════════════════════════════╗
 ║  🌐  UserController                        ║  api-portal/user/controller/
 ║  @PostMapping("/api/users")               ║
 ║  @Valid  ◄── Bean Validation (Tầng 1) 🛡️  ║
 ║  inject: ICreateUserUseCase               ║
 ║  inject: UserApiMapper                    ║
 ╚═══════════════════════════════╤═══════════╝
                    ①            │  userApiMapper.toCommand(request)
                                 ▼
                 ┌───────────────────────────────┐
                 │  📋 CreateUserCommandDto       │  application/dto/
                 │  (username, email, password)   │  ← record, String fields
                 └───────────────┬───────────────┘
                    ②            │  createUserUseCase.execute(command)
                                 ▼
 ╔═══════════════════════════════════════════╗
 ║  ⚙️  CreateUserUseCaseImpl                ║  application/usecase/
 ║  @Service @Transactional                  ║
 ║                                           ║
 ║  ① EmailVO.of()    ◄── wrap + validate 💎 ║
 ║     UsernameVO.of()    (Tầng 2 validate)  ║
 ║  ② userDomainService                     ║──► 🧠 UserDomainService
 ║       .validateUniqueConstraints()        ║         │
 ║  ③ passwordEncoder.encode() 🔐           ║         └──► IUserRepository
 ║  ④ User.create(String, String, String)   ║               → UserPostgresqlAdapter
 ║  ⑤ userRepository.save(user)            ║──► 🔌 IUserRepository (port/out)
 ╚═══════════════════════════════╤═══════════╝
                                 │  Spring inject UserPostgresqlAdapter
                                 ▼
 ╔═══════════════════════════════════════════╗
 ║  🔌 UserPostgresqlAdapter                 ║  infrastructure/persistence/postgresql/
 ║  implements IUserRepository               ║    adapter/
 ║                                           ║
 ║  mapper.toJpaEntity(user) 🔄             ║
 ║    ├── .username(u.getValue())  ← unwrap  ║
 ║    ├── .email(e.getValue())     ← unwrap  ║
 ║    └── .passwordHash(p.get())   ← unwrap  ║
 ║  jpaRepo.save(jpaEntity) ─────────────────╫──► 🗃️  IUserJpaRepository
 ║  mapper.toDomain(saved)  🔄              ║
 ╚═══════════════════════════════╤═══════════╝
                                 │  Spring Data JPA → generate SQL
                                 ▼
                 ╔═══════════════════════════════╗
                 ║  🐘 PostgreSQL                 ║
                 ║  INSERT INTO users VALUES (...) ║
                 ╚═══════════════╤═══════════════╝
                                 │  return saved entity
                                 │
                    ↑ ngược chiều lên trên ↑
                                 │
                                 ▼
                 ┌───────────────────────────────┐
                 │  📦 UserResponseDto.from(user) │  application/dto/
                 │  { id, username, email,        │
                 │    role, status, createdAt }   │
                 └───────────────┬───────────────┘
                    ③            │  userApiMapper.toResponse(appDto)
                                 ▼
                 ┌───────────────────────────────┐
                 │  📤 UserResponse               │  api-portal/user/response/
                 │  ← HTTP contract riêng biệt   │  ≠ UserResponseDto (app DTO)
                 └───────────────┬───────────────┘
                                 │  HTTP 201 Created
                                 ▼
  👤 CLIENT ✅

  ─────────────────────────────────────────────────────────────────
  🔴 Lỗi trong luồng:
     MethodArgumentNotValidException  ──►  GlobalExceptionHandler  ──►  400 BAD REQUEST
     UserDomainException              ──►  GlobalExceptionHandler  ──►  409 CONFLICT


╔══════════════════════════════════════════════════════════════════════════════════╗
║  📋 THỨ TỰ TẠO FILE (30 files)                                                   ║
╚══════════════════════════════════════════════════════════════════════════════════╝

  ── 📦 user-service (library module) ──────────────────────────────────────────

  [01] 🏷️  domain/enums/UserStatusEnum.java
  [02] 🏷️  domain/enums/UserRoleEnum.java
  [03] 🚨  domain/exception/UserDomainException.java
  [04] 💎  domain/valueobject/EmailVO.java                   ← depends on [03]
  [05] 💎  domain/valueobject/UsernameVO.java                ← depends on [03]
  [06] 💎  domain/valueobject/PasswordHashVO.java            ← depends on [03]
  [07] ⭐  domain/model/User.java                            ← depends on [01][02][03][04][05][06]
  [08] 🔌  domain/port/out/IUserRepository.java              ← depends on [04][05][07]
             (+ findByUsername — auth support)
  [09] 🧠  domain/service/UserDomainService.java             ← depends on [03][04][05][08]
  [10] 📋  application/dto/CreateUserCommandDto.java
  [11] 📋  application/dto/UserResponseDto.java              ← depends on [01][02][07]
  [12] 📋  application/dto/UserCredentialDto.java            ← auth support: userId, username,
             passwordHash, role, status
  [13] 🚪  application/port/in/ICreateUserUseCase.java       ← depends on [10][11]
  [14] 🚪  application/port/in/IGetUserCredentialUseCase.java ← auth support, depends on [12]
  [15] ⚙️   application/usecase/CreateUserUseCaseImpl.java   ← depends on [04][05][07][08][09][10][11][13]
  [16] ⚙️   application/usecase/GetUserCredentialUseCaseImpl.java  ← auth support, depends on [05][08][12][14]
  [17] 🧩  infrastructure/.../entity/UserJpaEntity.java      ← depends on [01][02]
  [18] 🗃️   infrastructure/.../repository/IUserJpaRepository.java      ← depends on [17]
             (+ findByUsername — auth support)
  [19] 🗃️   infrastructure/.../repository/UserPostgresqlQueryRepository.java ← standalone
  [20] 🔄  infrastructure/.../mapper/UserPersistenceMapper.java         ← depends on [07][17]
  [21] 🔌  infrastructure/.../adapter/UserPostgresqlAdapter.java        ← depends on [04][05][08][18][19][20]
             (+ findByUsername — auth support)
  [22] ⚙️   infrastructure/config/UserDomainConfig.java      ← depends on [08][09]
             @Bean userDomainService + @Bean passwordEncoder (BCrypt)

  ── 🌐 api-portal (runnable Spring Boot app) ──────────────────────────────────

  [23] 🔐  config/SecurityConfig.java         ← HTTP security (PasswordEncoder ở UserDomainConfig)
  [24] 📖  config/OpenApiConfig.java          ← Swagger/OpenAPI setup
  [25] 🛡️   exception/GlobalExceptionHandler.java           ← depends on [03]
  [26] 📥  user/request/CreateUserRequest.java               ← HTTP input validation
  [27] 📤  user/response/UserResponse.java                   ← HTTP response contract
  [28] 🔄  user/mapper/UserApiMapper.java     ← depends on [10][11][26][27]
  [29] 🎮  user/controller/UserController.java               ← depends on [13][28]
  [30] 🚀  ApiPortalApplication.java          ← scanBasePackages + @EnableJpaRepositories + @EntityScan
```
