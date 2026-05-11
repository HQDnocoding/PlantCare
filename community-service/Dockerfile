# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/community-service-*.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

USER appuser

EXPOSE 8084

ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
