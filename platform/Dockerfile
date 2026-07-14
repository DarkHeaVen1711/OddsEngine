# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=builder /app/build/libs/*.jar app.jar

# SQLite data volume mount point
VOLUME /app/data

# Platform REST API port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.datasource.url=jdbc:sqlite:/app/data/db.sqlite3"]
