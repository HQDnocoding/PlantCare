# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /build/target/search-service-*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create logs directory with proper permissions
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app && chmod 755 /app && chmod 755 /app/logs

USER appuser

EXPOSE 8085

ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]