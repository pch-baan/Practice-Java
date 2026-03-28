# mid-size.services

Practice project — **DDD + Hexagonal Architecture** trên **Modular Monolith** với Java 21 và Spring Boot 3.5.

---

## Tech Stack

| Component       | Technology                              |
|-----------------|-----------------------------------------|
| Language        | Java 21                                 |
| Framework       | Spring Boot 3.5.11                      |
| Build           | Maven (multi-module)                    |
| Database        | PostgreSQL 16                           |
| Migrations      | Flyway 11.7.2                           |
| Messaging       | RabbitMQ 4 (3-node cluster)             |
| Auth            | JWT (jjwt 0.12.6) + Spring Security     |
| Mapping         | MapStruct 1.5.5 + Lombok                |
| API Docs        | SpringDoc OpenAPI (Swagger UI)          |
| Containerize    | Docker + Docker Compose                 |

---

## Architecture

**Modular Monolith** — các module là Maven JAR libraries, chạy trong một JVM duy nhất qua `api-portal`.

```
practice.services/
├── user-service/     ← JAR library — User domain
├── auth-service/     ← JAR library — Auth domain (depends on user-service)
├── api-portal/       ← Runnable Spring Boot app :8080 (REST, Security, Swagger)
├── worker-service/   ← Runnable Spring Boot app :8083 (RabbitMQ consumer, Email)
└── sandbox/          ← Learning playground
```

Mỗi domain module (`user-service`, `auth-service`) có 4 tầng nội bộ:

```
domain/          ← Pure Java — không có Spring, JPA. Entities, Value Objects, Domain Services.
application/     ← Orchestration — UseCases, DTOs, Port interfaces (in/ và out/).
infrastructure/  ← Technical details — JPA, JWT, RabbitMQ adapters.
interfaces/      ← (future) gRPC hoặc inbound adapters khác.
```

**Dependency rule:**

```
api-portal (controllers) → application (UseCases) → domain
                                    ↑
                             infrastructure (adapts to domain ports)
```

---

## Event-Driven Flow (RabbitMQ)

```
RegisterUseCaseImpl
  → ApplicationEventPublisher.publishEvent(UserRegisteredEvent)
      → UserRegisteredEventListener (@TransactionalEventListener AFTER_COMMIT)
          → RabbitMQUserRegisteredPublisher (auth.exchange / user.registered)
              → worker-service: UserRegisteredNotificationConsumer
                  → ProcessedMessageTracker.tryMarkAsProcessed()  [idempotency]
                  → IWorkerEmailPort.sendVerificationEmail()
```

**Exchange / Queue config:**

| Resource         | Name                                    | Type   |
|------------------|-----------------------------------------|--------|
| Exchange         | `auth.exchange`                         | TOPIC  |
| Queue            | `notification.user.registered`          | —      |
| Routing Key      | `user.registered`                       | —      |
| DLX              | `worker.dlx`                            | DIRECT |
| DLQ              | `notification.user.registered.dlq`      | —      |
| DLQ Routing Key  | `notification.user.registered.failed`   | —      |

- **Retry policy:** 3 attempts, exponential backoff 1s → 2s → 4s. Sau đó route to DLQ.
- **Idempotency:** `ProcessedMessageTracker` in-memory (TTL 24h, cleanup mỗi giờ).
- **Transactional guarantee:** `@TransactionalEventListener(AFTER_COMMIT)` — không gửi message khi DB rollback.

---

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### 1. Start infrastructure

```bash
docker-compose up postgres rabbitmq1 -d
```

### 2. Run api-portal (dev mode)

```bash
cd api-portal && mvn spring-boot:run
```

### 3. Run worker-service (dev mode)

```bash
cd worker-service && mvn spring-boot:run
```

### 4. Full stack (Docker)

```bash
docker-compose up --build
```

---

## Build & Test

```bash
# Build all modules
mvn clean package

# Build skipping tests
mvn clean package -DskipTests

# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl user-service
mvn test -pl auth-service
mvn test -pl api-portal

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

---

## Local Infrastructure

| Service       | Port  | Notes                                      |
|---------------|-------|--------------------------------------------|
| `postgres`    | 5432  | PostgreSQL 16, DB: `practice_db`           |
| `rabbitmq1`   | 5672 / 15672 | RabbitMQ node 1 (primary)          |
| `rabbitmq2`   | 5673 / 15673 | RabbitMQ node 2                    |
| `rabbitmq3`   | 5674 / 15674 | RabbitMQ node 3                    |
| `adminer`     | 5050  | Web UI for database inspection             |
| `api-portal`  | 8080  | Spring Boot app (commented out by default) |
| `worker-service` | 8083 | Async worker (commented out by default) |

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **RabbitMQ Management UI:** `http://localhost:15672` (guest/guest)
- **Adminer:** `http://localhost:5050`

---

## Environment Variables

```env
# Database (api-portal)
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/practice_db
DB_USERNAME=practice_user
DB_PASSWORD=practice_pass

# JWT (api-portal)
JWT_SECRET=<base64-encoded secret>
JWT_EXPIRATION_MS=86400000

# RabbitMQ (api-portal)
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672

# RabbitMQ (worker-service)
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# Email (worker-service — optional, falls back to NoOp if not set)
MAIL_HOST=<smtp-host>
MAIL_PORT=587
MAIL_USERNAME=<email>
MAIL_PASSWORD=<password>
APP_BASE_URL=http://localhost:8080
```

---

## Key Technical Decisions

- **Flyway manages schema** — `jpa.hibernate.ddl-auto: validate` (Hibernate chỉ verify, không tự alter).
- Migration files: `api-portal/src/main/resources/db/migration/`. Format: `V{timestamp}__{description}.sql`. Không sửa migration đã deploy.
- **MapStruct** generate mappers lúc compile. Lombok và MapStruct annotation processors khai báo đúng thứ tự trong `pom.xml`.
- **Spring Security** stateless (no session). JWT filter handle auth. Public paths: `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`.
- **Email** hoàn toàn thuộc `worker-service`. Khi `MAIL_HOST` không được set, tự động dùng `WorkerNoOpEmailAdapter` (dev/test).
- **auth-service** gọi `user-service` chỉ qua port interfaces — không đọc DB trực tiếp.

---

## Documentation

| File / Directory     | Nội dung                                          |
|----------------------|---------------------------------------------------|
| `architecture.md`    | DDD + Hexagonal patterns, layer rules, flow diagrams |
| `.docs/maven/`       | Maven build lifecycle, multi-module setup         |
| `.docs/docker/`      | Dockerfile multi-stage build, Docker Compose      |
| `.docs/flyway/`      | Migration naming, Flyway configuration            |
| `.docs/postgresql/`  | PostgreSQL + Spring Boot integration              |
| `.docs/hibernate/`   | Hibernate/JPA entity mapping                      |
