# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom files first (cache dependencies layer)
COPY pom.xml .
COPY user-service/pom.xml user-service/
COPY auth-service/pom.xml auth-service/
COPY worker-service/pom.xml worker-service/
COPY api-portal/pom.xml api-portal/
COPY sandbox/pom.xml sandbox/

# Download dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -pl user-service,auth-service,worker-service,api-portal -am -q

# Copy source code
COPY user-service/src user-service/src
COPY auth-service/src auth-service/src
COPY worker-service/src worker-service/src
COPY api-portal/src api-portal/src

# Build — skip tests for faster image build
RUN mvn clean package -DskipTests -pl user-service,auth-service,worker-service,api-portal -am -q

# ============================================================
# Stage 2: Run
# ============================================================
FROM eclipse-temurin:21-jre

ARG TARGET_SERVICE=api-portal
ARG EXPOSE_PORT=8080

WORKDIR /app

# Copy target dir then pick the single fat JAR (avoids glob+ARG expansion issues)
COPY --from=builder /app/${TARGET_SERVICE}/target/ /tmp/target/
RUN find /tmp/target -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" \
    -exec mv {} /app/app.jar \; && rm -rf /tmp/target

EXPOSE ${EXPOSE_PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
