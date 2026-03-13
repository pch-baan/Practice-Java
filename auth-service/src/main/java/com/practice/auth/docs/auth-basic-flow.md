
# AUTH BASIC FEATURE — THU TU TAO FILE & LUONG DU LIEU

## NGUYEN TAC
- Tao tu TRONG ra NGOAI (domain truoc, api-portal sau)
- auth-service la **library module** (jar) — REST controller nam o api-portal/auth/
- auth-service KHONG doc `users` table truc tiep
  → goi qua port/in cua user-service (Option B)
  → dependency explicit, compiler bat loi khi schema thay doi

## FEATURES
- POST /api/auth/login    — username + password → JWT access token + refresh token
- POST /api/auth/refresh  — refresh token       → new access token
- POST /api/auth/logout   — refresh token       → revoke

## LUONG PHU THUOC (Option B)

```
auth-service  ──dep──►  user-service  (Maven dependency)

AuthController
  → LoginUseCaseImpl
      → UserCredentialServiceAdapter      ← infrastructure/external/
          → IGetUserCredentialUseCase     ← port/in cua user-service
              → GetUserCredentialUseCaseImpl
                  → IUserRepository
                      → users table       ← chi user-service duoc doc DB cua no
```

---

## BUOC 0 — USER-SERVICE: expose port/in moi cho auth

```
user-service/application/

├── dto/
│   └── UserCredentialDto.java         ← record (moi): userId, username,
│                                                        passwordHash, role, status

├── port/in/
│   └── IGetUserCredentialUseCase.java ← (moi)
│       findByUsername(String): Optional<UserCredentialDto>
│       findByUserId(UUID): Optional<UserCredentialDto>

└── usecase/
    └── GetUserCredentialUseCaseImpl.java ← @Service (moi)
        findByUsername(username):
            userRepository.findByUsername(UsernameVO.of(username))
                .map(user -> new UserCredentialDto(
                    user.getId(),
                    user.getUsername().getValue(),
                    user.getPasswordHash().getValue(),
                    user.getRole().name(),
                    user.getStatus().name()
                ))
        findByUserId(userId):
            userRepository.findById(userId).map(...)
```

> user-service phai them:
> - findByUsername vao IUserRepository va UserPostgresqlAdapter
> - findByUsername vao IUserJpaRepository
> - @Bean passwordEncoder() vao UserDomainConfig  ← tranh implicit dep (xem D6)

---

## BUOC 1 — DOMAIN LAYER (pure Java, khong import framework)

```
auth-service/domain/

├── enums/
│   └── TokenTypeEnum.java            ← ACCESS | REFRESH  [DONE]

├── exception/
│   └── AuthDomainException.java      ← business exception (NOT HTTP)  [DONE]

├── model/
│   ├── UserCredential.java           ← Read model (pure Java record)  [DONE]
│   │   UUID userId, String username, String passwordHash, String role, String status
│   │
│   └── RefreshToken.java             ← Domain entity  [DONE]
│       UUID id, UUID userId, String tokenHash, LocalDateTime expiresAt
│       boolean revoked, LocalDateTime createdAt
│       + static create(userId, tokenHash, expiresAt)
│       + static reconstruct(id, userId, hash, expiresAt, revoked, createdAt)
│       + isExpired() / isValid() / revoke()

├── port/out/
│   ├── IRefreshTokenRepository.java  [DONE]
│   │   save(RefreshToken): RefreshToken
│   │   findByTokenHash(String): Optional<RefreshToken>
│   │   revokeByTokenHash(String): void
│   │   revokeAllByUserId(UUID): void
│   │
│   └── IUserCredentialPort.java      ← READ-ONLY, duoc implement boi ServiceAdapter  [DONE]
│       findByUsername(String): Optional<UserCredential>
│       findByUserId(UUID): Optional<UserCredential>

└── service/
    └── AuthDomainService.java        ← KHONG co @Service (pure Java)  [DONE]
        validateUserCanLogin(UserCredential)
            → if status != ACTIVE → throw AuthDomainException
        validateRefreshToken(RefreshToken)
            → if !token.isValid() → throw AuthDomainException
```

---

## BUOC 2 — APPLICATION LAYER (biet Spring @Service, PasswordEncoder; khong biet JPA/JWT impl)

```
auth-service/application/

├── dto/
│   ├── LoginCommandDto.java           ← record: String username, String password
│   ├── RefreshTokenCommandDto.java    ← record: String rawRefreshToken
│   └── AuthTokenDto.java              ← record: String accessToken, String refreshToken,
│                                                  String tokenType, long expiresInMs

├── port/in/
│   ├── ILoginUseCase.java             → execute(LoginCommandDto): AuthTokenDto
│   ├── IRefreshTokenUseCase.java      → execute(RefreshTokenCommandDto): AuthTokenDto
│   └── ILogoutUseCase.java            → execute(String rawRefreshToken): void

├── port/out/
│   └── IJwtPort.java                  ← interface — app khong biet jjwt
│       generateAccessToken(UUID userId, String role): String
│       getExpirationMs(): long

└── usecase/
    ├── LoginUseCaseImpl.java          ← @Service @Transactional
    │   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    │   (PasswordEncoder bean o UserDomainConfig (user-service) — IntelliJ
    │    khong trace cross-module, suppress de tranh false positive)
    │   execute(command):
    │   ① userCredentialPort.findByUsername(username)
    │        → empty? throw AuthDomainException "Invalid credentials"
    │   ② passwordEncoder.matches(password, credential.passwordHash())
    │        → false? throw AuthDomainException "Invalid credentials"
    │        (generic error — tranh tiet lo user co ton tai khong)
    │   ③ authDomainService.validateUserCanLogin(credential)
    │   ④ String rawToken = UUID.randomUUID().toString()
    │   ⑤ String tokenHash = sha256(rawToken)
    │   ⑥ RefreshToken.create(userId, tokenHash, now + refreshExpiry)
    │   ⑦ refreshTokenRepository.save(refreshToken)
    │   ⑧ String accessToken = jwtPort.generateAccessToken(userId, role)
    │   ⑨ return AuthTokenDto(accessToken, rawToken, "Bearer", expMs)
    │
    ├── RefreshTokenUseCaseImpl.java   ← @Service @Transactional
    │   execute(command):
    │   ① sha256(command.rawRefreshToken()) → tokenHash
    │   ② refreshTokenRepository.findByTokenHash(hash)
    │        → empty? throw AuthDomainException "Invalid token"
    │   ③ authDomainService.validateRefreshToken(refreshToken)
    │   ④ userCredentialPort.findByUserId(refreshToken.getUserId())
    │   ⑤ authDomainService.validateUserCanLogin(credential)
    │   ⑥ jwtPort.generateAccessToken(userId, role)
    │   ⑦ return AuthTokenDto(newAccessToken, null, "Bearer", expMs)
    │
    └── LogoutUseCaseImpl.java         ← @Service @Transactional
        execute(rawRefreshToken):
        ① sha256(rawRefreshToken) → tokenHash
        ② refreshTokenRepository.revokeByTokenHash(tokenHash)
```

---

## BUOC 3 — INFRASTRUCTURE LAYER (biet tat ca: JPA, JWT, SHA-256)

```
auth-service/infrastructure/

├── external/                          ← goi sang service khac trong monolith
│   └── UserCredentialServiceAdapter.java  ← @Repository, implements IUserCredentialPort
│       @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
│       inject IGetUserCredentialUseCase   ← Spring inject tu user-service
│       (bean o user-service — IntelliJ khong trace cross-module)
│       findByUsername(username):
│           getUserCredentialUseCase.findByUsername(username)
│               .map(dto -> new UserCredential(dto.userId(), dto.username(),
│                           dto.passwordHash(), dto.role(), dto.status()))
│       findByUserId(userId):
│           getUserCredentialUseCase.findByUserId(userId).map(...)

├── config/
│   ├── JwtProperties.java             ← @ConfigurationProperties("jwt")
│   │   String secret, long expirationMs
│   │
│   └── AuthDomainConfig.java          ← @Configuration
│       @Bean authDomainService() → new AuthDomainService()
│       (domain service khong co @Service — phai wire thu cong)

├── security/
│   └── JwtServiceImpl.java            ← @Component, implements IJwtPort
│       inject JwtProperties
│       generateAccessToken(UUID userId, String role):
│           Jwts.builder()
│               .subject(userId.toString())
│               .claim("role", role)
│               .issuedAt(now)
│               .expiration(now + expirationMs)
│               .signWith(hmacKey)
│               .compact()

└── persistence/postgresql/

    ├── entity/
    │   └── RefreshTokenJpaEntity.java     ← @Entity @Table("refresh_tokens")
    │       @Id UUID id
    │       UUID userId
    │       String tokenHash              ← unique
    │       LocalDateTime expiresAt
    │       boolean revoked               ← default false
    │       LocalDateTime createdAt       ← updatable=false

    ├── repository/
    │   └── IRefreshTokenJpaRepository.java   ← extends JpaRepository
    │       findByTokenHash(String): Optional<RefreshTokenJpaEntity>
    │       @Modifying @Query update revoked=true where tokenHash=?1
    │       @Modifying @Query update revoked=true where userId=?1

    ├── mapper/
    │   └── RefreshTokenPersistenceMapper.java ← @Component
    │       toJpaEntity(RefreshToken): RefreshTokenJpaEntity
    │       toDomain(entity): RefreshToken.reconstruct(...)

    └── adapter/
        └── RefreshTokenPostgresqlAdapter.java  ← @Repository implements IRefreshTokenRepository
            save()     → mapper.toJpa() → jpaRepo.save() → mapper.toDomain()
            findByTokenHash() → jpaRepo.findByTokenHash().map(mapper::toDomain)
            revokeByTokenHash() → jpaRepo.updateRevokedByHash()
            revokeAllByUserId() → jpaRepo.updateRevokedByUserId()
```

---

## BUOC 4 — API LAYER (api-portal/auth/)

```
api-portal/auth/

├── request/
│   ├── LoginRequest.java              ← @NotBlank username, @NotBlank @Size(min=8) password
│   └── RefreshTokenRequest.java       ← @NotBlank String refreshToken

├── response/
│   └── AuthTokenResponse.java         ← String accessToken, refreshToken, tokenType, long expiresIn

├── mapper/
│   └── AuthApiMapper.java             ← @Component
│       toLoginCommand(LoginRequest) → LoginCommandDto
│       toRefreshCommand(RefreshTokenRequest) → RefreshTokenCommandDto
│       toResponse(AuthTokenDto) → AuthTokenResponse

└── controller/
    └── AuthController.java            ← @RestController @RequestMapping("/api/auth")
        POST /login   → @Valid LoginRequest → mapper → loginUseCase → 200 OK
        POST /refresh → @Valid RefreshTokenRequest → ... → 200 OK
        POST /logout  → String rawToken → logoutUseCase → 204 NO CONTENT
```

### UPDATE (khong tao moi):

```
api-portal/config/SecurityConfig.java
    + sessionManagement: STATELESS
    + permitAll: /api/auth/**, /swagger-ui/**, /v3/api-docs/**
    NOTE: PasswordEncoder KHONG khai bao o day
          → da chuyen sang user-service/UserDomainConfig (xem D6)

api-portal/exception/GlobalExceptionHandler.java
    + AuthDomainException → 401 UNAUTHORIZED

api-portal/src/main/resources/application.yml
    + jwt:
        secret: ${JWT_SECRET:practice-secret-key-must-be-at-least-256-bits-long}
        expiration-ms: ${JWT_EXPIRATION_MS:86400000}
    + auth:
        refresh-token:
          expiration-days: ${AUTH_REFRESH_EXPIRATION_DAYS:30}
```

### POM UPDATE:

```xml
<!-- auth-service/pom.xml — them dependency -->
<dependency>
    <groupId>com.practice</groupId>
    <artifactId>user-service</artifactId>
    <version>${project.version}</version>
</dependency>
```

```
<!-- auth-service/pom.xml — XOA (library module, khong phai runnable app) -->
- spring-boot-starter-web       (khong co REST controller — nam o api-portal)
- springdoc-openapi-starter-*   (Swagger cua api-portal, khong phai auth-service)
- <build> spring-boot-maven-plugin  (fat jar lam hong Maven dependency cho api-portal)
```

---

## THU TU TAO FILE

```
── user-service (them moi) ─────────────────────────────────────────────

[U1] application/dto/UserCredentialDto.java                               [DONE]
[U2] application/port/in/IGetUserCredentialUseCase.java    ← depends on [U1]  [DONE]
[U3] application/usecase/GetUserCredentialUseCaseImpl.java ← depends on [U1][U2]  [DONE]
     (+ update IUserRepository: them findByUsername)
     (+ update UserPostgresqlAdapter: implement findByUsername)

── auth-service ────────────────────────────────────────────────────────

[01] domain/enums/TokenTypeEnum.java                              [DONE]
[02] domain/exception/AuthDomainException.java                    [DONE]
[03] domain/model/UserCredential.java                             [DONE]
[04] domain/model/RefreshToken.java                               [DONE]
[05] domain/port/out/IRefreshTokenRepository.java                 [DONE]
[06] domain/port/out/IUserCredentialPort.java                     [DONE]
[07] domain/service/AuthDomainService.java                        [DONE]
[08] application/dto/LoginCommandDto.java                                 [DONE]
[09] application/dto/RefreshTokenCommandDto.java                         [DONE]
[10] application/dto/AuthTokenDto.java                                   [DONE]
[11] application/port/in/ILoginUseCase.java                ← depends on [08][10]  [DONE]
[12] application/port/in/IRefreshTokenUseCase.java         ← depends on [09][10]  [DONE]
[13] application/port/in/ILogoutUseCase.java                             [DONE]
[14] application/port/out/IJwtPort.java                                  [DONE]
[15] application/usecase/LoginUseCaseImpl.java             ← depends on [03][05][06][07][08][10][11][14]  [DONE]
[16] application/usecase/RefreshTokenUseCaseImpl.java      ← depends on [03][05][06][07][09][10][12][14]  [DONE]
[17] application/usecase/LogoutUseCaseImpl.java            ← depends on [05][13]  [DONE]
[18] infrastructure/persistence/postgresql/entity/RefreshTokenJpaEntity.java      [DONE]
[19] infrastructure/persistence/postgresql/repository/IRefreshTokenJpaRepository.java  ← depends on [18]  [DONE]
[20] infrastructure/persistence/postgresql/mapper/RefreshTokenPersistenceMapper.java   ← depends on [04][18]  [DONE]
[21] infrastructure/persistence/postgresql/adapter/RefreshTokenPostgresqlAdapter.java  ← depends on [05][19][20]  [DONE]
[22] infrastructure/external/UserCredentialServiceAdapter.java   ← depends on [03][06][U2]  [DONE]
[23] infrastructure/security/JwtServiceImpl.java           ← implements [14]      [DONE]
[24] infrastructure/config/JwtProperties.java                            [DONE]
[25] infrastructure/config/AuthDomainConfig.java                         [DONE]

── api-portal/auth/ ────────────────────────────────────────────────────

[26] auth/request/LoginRequest.java                                      [DONE]
[27] auth/request/RefreshTokenRequest.java                               [DONE]
[28] auth/response/AuthTokenResponse.java                                [DONE]
[29] auth/mapper/AuthApiMapper.java                        ← depends on [08][09][10][26][27][28]  [DONE]
[30] auth/controller/AuthController.java                   ← depends on [11][12][13][29]          [DONE]

── api-portal UPDATES ──────────────────────────────────────────────────

[P1] auth-service/pom.xml              UPDATE: them user-service dependency  [DONE]
[P2] config/SecurityConfig.java        UPDATE: STATELESS + permit /api/auth/**  [DONE]
[P3] exception/GlobalExceptionHandler.java  UPDATE: AuthDomainException → 401  [DONE]
[P4] src/main/resources/application.yml    UPDATE: them jwt + auth config    [DONE]
[P5] user-service/infrastructure/config/UserDomainConfig.java                [DONE]
         UPDATE: them @Bean passwordEncoder() → tranh implicit cross-module dep

── KHONG can tao ───────────────────────────────────────────────────────

✅ DB migration: da co V20260313100001__create_refresh_tokens_table.sql
✅ RefreshTokenPostgresqlQueryRepository.java — giu nguyen (placeholder)
```

---

## DESIGN DECISIONS

### D1 — Option B thay vi Option A: auth-service goi qua port/in cua user-service

**Quyet dinh:** `UserCredentialServiceAdapter` (infrastructure/external/) goi `IGetUserCredentialUseCase`
thay vi `UserCredentialJpaEntity` doc thang `users` table.

**Option A bi loai vi schema coupling an:**

```
Option A (bi loai):

user-service                    auth-service
     │                               │
     │  owns & writes                │  doc truc tiep qua JPA
     ▼                               ▼
┌─────────────────────────────────────────┐
│              users table                │
│  id, username, password_hash, role...   │
└─────────────────────────────────────────┘
              ↑
        SCHEMA COUPLING AN
        compiler khong biet, runtime moi loi
```

Vi du loi:
- user-service doi ten cot `password_hash` → `hashed_password`
- `UserCredentialJpaEntity` trong auth-service van compile OK
- Chi den luc chay moi bao loi: "column hashed_password does not exist"

**Option B duoc chon vi:**

```
Option B (da chon):

auth-service  ──dep──►  user-service  (Maven dependency tuong minh)

                    IGetUserCredentialUseCase  ← Java interface
                              ↑
                    compiler bat loi ngay khi
                    user-service thay doi contract
```

- Dependency explicit, khong an → ai doc code cung thay ngay
- Schema thay doi → compile error → bat loi som (fail fast)
- `infrastructure/external/` dung dung muc dich theo architecture.md
- De tach microservice: chi can doi implementation tu in-process → HTTP, khong sua auth-service

**Trade-off cua Option B:**
- auth-service phu thuoc user-service module (explicit dep)
- Neu tach microservice that su: phai tach dep nay ra HTTP client
- Them 3 files o user-service (UserCredentialDto, IGetUserCredentialUseCase, GetUserCredentialUseCaseImpl)

---

| # | Quyet dinh | Ly do |
|---|---|---|
| D2 | Refresh token hash = SHA-256(rawToken) | DB leak khong lo token that; UUID co du entropy cho SHA-256 |
| D3 | IJwtPort o application/port/out/ | App layer khong import jjwt truc tiep → de mock trong unit test |
| D4 | AuthDomainException → 401 (khong phai 403) | 401 = chua xac thuc; 403 = da xac thuc nhung khong co quyen |
| D5 | Error message generic "Invalid credentials" | Tranh user enumeration attack |
| D6 | @Bean PasswordEncoder o UserDomainConfig (user-service), khong phai api-portal | Library module khong duoc phu thuoc ngam vao host app de cung cap bean — implicit contract gay runtime crash neu doi host |
