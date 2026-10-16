# ===== Build stage =====
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ===== Run stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Seoul
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
