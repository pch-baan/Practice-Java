# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build all modules
mvn clean package

# Build skipping tests (used in Docker)
mvn clean package -DskipTests

# Run in dev mode (requires postgres + rabbitmq running)
docker-compose up postgres rabbitmq -d
cd api-portal && mvn spring-boot:run

# Run worker-service locally
cd worker-service && mvn spring-boot:run

# Run full stack (build image + start all services)
docker-compose up --build

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

## Architecture

**Modular Monolith** with DDD layering across Maven modules (Java 21, Spring Boot 3.5.11):

- **`user-service`** — JAR library. User management domain.
- **`auth-service`** — JAR library. Authentication domain. Depends on `user-service`.
- **`api-portal`** — Runnable Spring Boot app (port 8080). REST controllers, Flyway migrations.
- **`worker-service`** — Runnable Spring Boot app (port 8083). Consumes RabbitMQ events, no database access. Has its own `application/port/` layer (`IWorkerEmailPort`) and `infrastructure/email/` with DLQ + idempotency support.
- **`sandbox`** — Learning playground. Not part of production flow.

Each domain module (`user-service`, `auth-service`) is internally structured into 4 layers:

```
domain/          ← Pure Java only. No Spring, no JPA. Entities, Value Objects, Enums, Domain Services.
application/     ← Orchestrates business flow. UseCases, DTOs, Port interfaces (in/ and out/).
infrastructure/  ← Technical details: JPA entities, repositories, JWT, adapters, messaging.
interfaces/      ← (future) gRPC or other inbound adapters per service.
```

The REST layer lives in `api-portal/` (controllers, request/response objects, API mappers).

**Dependency rule:** `api-portal (controllers)` → `application (UseCases)` → `domain`. Infrastructure adapts to domain ports, never the reverse.

**Inter-service communication:** `auth-service` calls `user-service` only through port interfaces (`IGetUserCredentialUseCase`, `ICreateUserUseCase`, `IActivateUserUseCase`) — never directly through infrastructure.

## Event-Driven Messaging (RabbitMQ)

Registration triggers an async event pipeline after transaction commit:

```
RegisterUseCaseImpl
    → publishes UserRegisteredEvent via Spring ApplicationEventPublisher
        → UserRegisteredEventListener (@TransactionalEventListener AFTER_COMMIT)
            → RabbitMQUserRegisteredPublisher (auth.exchange / user.registered)
                → worker-service: UserRegisteredNotificationConsumer
                    → ProcessedMessageTracker.tryMarkAsProcessed() [idempotency]
                    → IWorkerEmailPort.sendVerificationEmail()
```

**Key design:** `@TransactionalEventListener(phase = AFTER_COMMIT)` ensures RabbitMQ only receives the message if the DB transaction succeeded — no ghost messages on rollback.

**Exchange/Queue config:**

| Resource      | Name                                    | Type   |
|---------------|-----------------------------------------|--------|
| Exchange      | `auth.exchange`                         | TOPIC  |
| Queue         | `notification.user.registered`          | —      |
| Routing Key   | `user.registered`                       | —      |
| DLX           | `worker.dlx`                            | DIRECT |
| DLQ           | `notification.user.registered.dlq`      | —      |
| DLQ Routing Key | `notification.user.registered.failed` | —      |

**Retry policy:** 3 attempts, exponential backoff 1s → 2s → 4s (max 10s). After all retries exhausted, message is routed to DLQ via DLX (`RejectAndDontRequeue`).

**Idempotency:** `ProcessedMessageTracker` deduplicates messages in-memory using `rawToken` as the key. TTL is 24h (matching email verification token expiry), with hourly cleanup via `@Scheduled`.

Both `api-portal` and `worker-service` must connect to the same RabbitMQ instance.

## Key Technical Decisions

- **Flyway manages schema** — `jpa.hibernate.ddl-auto: validate` (Hibernate only verifies, never alters).
- Migration files are in `api-portal/src/main/resources/db/migration/`. Name format: `V{timestamp}__{description}.sql`. Never modify deployed migrations.
- **MapStruct** generates mappers at compile time. Lombok and MapStruct annotation processors must be declared in correct order in `pom.xml`.
- **JWT** via `jjwt 0.12.6` in `auth-service`. Configuration via `JwtProperties` (loaded from `application.yml` or env vars). Tokens contain: subject (userId), role claim, expiration.
- **Spring Security** is stateless (no session). JWT filter handles auth. Public paths: `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`.
- **Email:** Fully owned by `worker-service`. Port: `IWorkerEmailPort` (in `worker-service/application/port/`). Uses `WorkerMailSenderAdapter` when SMTP host is configured, falls back to `WorkerNoOpEmailAdapter` (dev/test). Condition class: `WorkerMailHostConfiguredCondition`. `auth-service` no longer contains any email infrastructure.

## Local Infrastructure (Docker Compose)

| Service      | Port  | Notes                                     |
|--------------|-------|-------------------------------------------|
| `postgres`   | 5432  | PostgreSQL 16, DB: `practice_db`          |
| `rabbitmq`   | 5672  | RabbitMQ 4, Management UI at port 15672   |
| `adminer`    | 5050  | Web UI for database inspection            |
| `api-portal` | 8080  | Spring Boot app (commented out by default)|

Swagger UI: `http://localhost:8080/swagger-ui.html`
RabbitMQ Management UI: `http://localhost:15672` (guest/guest)

## Environment Variables

Set in `docker-compose.yml` or `application-local.yml`:

```
SPRING_DATASOURCE_URL    jdbc:postgresql://postgres:5432/practice_db
DB_USERNAME              practice_user
DB_PASSWORD              practice_pass
JWT_SECRET               <base64-encoded secret>
JWT_EXPIRATION_MS        86400000

# RabbitMQ (api-portal)
SPRING_RABBITMQ_HOST     rabbitmq
SPRING_RABBITMQ_PORT     5672

# RabbitMQ (worker-service — different env var names)
RABBITMQ_HOST            rabbitmq
RABBITMQ_PORT            5672
RABBITMQ_USERNAME        guest
RABBITMQ_PASSWORD        guest

# Email (worker-service)
MAIL_HOST                <smtp-host>
MAIL_PORT                587
MAIL_USERNAME            <email>
MAIL_PASSWORD            <password>
APP_BASE_URL             http://localhost:8080
```

## Documentation

- `architecture.md` — DDD + Hexagonal patterns, dependency rules, layer guidelines
- `.docs/maven/` — Maven build lifecycle, multi-module setup
- `.docs/docker/` — Dockerfile multi-stage build, Docker Compose
- `.docs/flyway/` — Migration naming, Flyway configuration
- `.docs/postgresql/` — PostgreSQL + Spring Boot integration
- `.docs/hibernate/` — Hibernate/JPA entity mapping
