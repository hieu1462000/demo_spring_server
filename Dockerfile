# =========================
# STAGE 1: BUILD
# =========================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy gradle wrapper + config
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source
COPY src src
COPY libs libs

# Build Spring Boot jar
RUN chmod +x gradlew && \
    ./gradlew clean bootJar --no-daemon

# =========================
# STAGE 2: RUN
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/build/libs/demo-0.0.1-SNAPSHOT-plain.jar app.jar

# Render cung cấp PORT env
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
