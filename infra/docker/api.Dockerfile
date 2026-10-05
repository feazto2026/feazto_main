# Spring Boot API image (modular monolith).
# Build context is the REPO ROOT (see docker-compose.yml), so all COPY paths
# are rooted at D:\feazto_main / codewild-food-platform/.
# Canonical backend source: services/api (Maven wrapper + pom.xml).
#
# Local dev note: most backend dev runs `./mvnw spring-boot:run` directly and
# uses `docker compose up redis` for infrastructure. This image is for
# reproducible builds, CI smoke tests, and full-stack `docker compose up --build`.

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy Maven metadata first for better layer caching.
COPY services/api/mvnw ./services/api/mvnw
COPY services/api/.mvn ./services/api/.mvn
COPY services/api/pom.xml ./services/api/pom.xml
WORKDIR /workspace/services/api

# Make wrapper executable and pre-fetch dependencies (tolerates missing wrapper on Windows checkout).
RUN chmod +x mvnw || true
RUN ./mvnw -B -q dependency:go-offline || mvn -B -q dependency:go-offline || true

# Now copy the full backend source and build.
WORKDIR /workspace
COPY services/api ./services/api
WORKDIR /workspace/services/api
RUN ./mvnw -B -DskipTests package || mvn -B -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# curl is needed for the compose healthcheck (actuator / api health endpoint).
RUN apk add --no-cache curl

# Non-root user for production readiness.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build --chown=appuser:appgroup /workspace/services/api/target/*.jar /app/app.jar

EXPOSE 8080
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=local

# Actuator health must be enabled in services/api for the ideal path;
# the compose healthcheck falls back to /api/v1/health.
HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=60s \
  CMD curl -fsS http://localhost:8080/actuator/health || curl -fsS http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
