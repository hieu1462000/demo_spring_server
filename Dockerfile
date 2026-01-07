# =========================
# Stage 1: Build
# =========================
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# =========================
# Stage 2: Run
# =========================
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Render cung cấp PORT
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
