# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom files first (cache dependencies layer)
COPY pom.xml .
COPY user-service/pom.xml user-service/
COPY auth-service/pom.xml auth-service/
COPY api-portal/pom.xml api-portal/
COPY sandbox/pom.xml sandbox/

# Download dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -q

# Copy source code
COPY user-service/src user-service/src
COPY auth-service/src auth-service/src
COPY api-portal/src api-portal/src

# Build — skip tests for faster image build
RUN mvn clean package -DskipTests -q

# ============================================================
# Stage 2: Run
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/api-portal/target/api-portal-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
