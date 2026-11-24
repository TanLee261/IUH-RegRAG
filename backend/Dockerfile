# =========================================================================
# Stage 1: Build the Spring Boot executable JAR
# =========================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source code and package
COPY src ./src
RUN mvn clean package -DskipTests -B

# =========================================================================
# Stage 2: Minimal runtime image
# =========================================================================
FROM eclipse-temurin:17-jre-jammy AS runner

WORKDIR /app

# Create a non-root system user for security
RUN useradd -m -u 1001 -s /bin/bash regrag

# Copy the built jar from builder stage
COPY --from=builder /build/target/regrag-backend-0.0.1-SNAPSHOT.jar app.jar

# Set up uploads directory with appropriate permissions
RUN mkdir -p /app/uploads/documents && chown -R regrag:regrag /app

USER regrag

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=db \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
