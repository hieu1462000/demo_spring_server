# =========================
# Stage 1: Build
# =========================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY . .
RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# =========================
# Stage 2: Run
# =========================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy đúng bootJar (KHÔNG phải plain)
COPY --from=builder /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
