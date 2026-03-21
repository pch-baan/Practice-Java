# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build all modules
mvn clean package

# Build skipping tests (used in Docker)
mvn clean package -DskipTests

# Run in dev mode (requires postgres running)
docker-compose up postgres -d
cd api-portal && mvn spring-boot:run

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

**Modular Monolith** with DDD layering across 3 Maven modules:

- **`user-service`** — JAR library (no `@SpringBootApplication`). User management domain.
- **`auth-service`** — JAR library. Authentication domain. Depends on `user-service`.
- **`api-portal`** — Runnable Spring Boot app. Entry point, REST controllers, Flyway migrations.

Each domain module (`user-service`, `auth-service`) is internally structured into 4 layers:

```
domain/          ← Pure Java only. No Spring, no JPA. Entities, Value Objects, Enums, Domain Services.
application/     ← Orchestrates business flow. UseCases, DTOs, Port interfaces (in/ and out/).
infrastructure/  ← Technical details: JPA entities, repositories, JWT, adapters.
interfaces/      ← (future) gRPC or other inbound adapters per service.
```

The REST layer lives in `api-portal/` (controllers, request/response objects, API mappers).

**Dependency rule:** `api-portal (controllers)` → `application (UseCases)` → `domain`. Infrastructure adapts to domain ports, never the reverse.

**Inter-service communication:** `auth-service` calls `user-service` only through the `IGetUserCredentialUseCase` port interface — never directly through infrastructure.

## Key Technical Decisions

- **Flyway manages schema** — `jpa.hibernate.ddl-auto: validate` (Hibernate only verifies, never alters).
- Migration files are in `api-portal/src/main/resources/db/migration/`. Name format: `V{timestamp}__{description}.sql`. Never modify deployed migrations.
- **MapStruct** generates mappers at compile time. Lombok and MapStruct annotation processors must be declared in correct order in `pom.xml`.
- **JWT** via `jjwt 0.12.6` in `auth-service`. Configuration via `JwtProperties` (loaded from `application.yml` or env vars).
- **Spring Security** is stateless (no session). JWT filter handles auth.

## Local Infrastructure (Docker Compose)

| Service    | Port | Notes                              |
|------------|------|------------------------------------|
| `postgres` | 5432 | PostgreSQL 16, DB: `practice_db`   |
| `adminer`  | 5050 | Web UI for database inspection     |
| `api-portal` | 8080 | Spring Boot app                  |

Swagger UI available at: `http://localhost:8080/swagger-ui.html`

## Environment Variables

Set in `docker-compose.yml` or `application-local.yml`:

```
SPRING_DATASOURCE_URL    jdbc:postgresql://postgres:5432/practice_db
DB_USERNAME              practice_user
DB_PASSWORD              practice_pass
JWT_SECRET               <base64-encoded secret>
JWT_EXPIRATION_MS        86400000
```

## Documentation

- `architecture.md` — DDD + Hexagonal patterns, dependency rules, layer guidelines
- `.docs/maven/` — Maven build lifecycle, multi-module setup
- `.docs/docker/` — Dockerfile multi-stage build, Docker Compose
- `.docs/flyway/` — Migration naming, Flyway configuration
- `.docs/postgresql/` — PostgreSQL + Spring Boot integration
- `.docs/hibernate/` — Hibernate/JPA entity mapping
