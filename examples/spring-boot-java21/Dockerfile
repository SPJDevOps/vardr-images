# ===== Stage 1: Build the application =====
FROM gradle:8.9-jdk21 AS builder

# Set work directory
WORKDIR /app

# Copy Gradle files first to leverage Docker cache
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle

# Download dependencies
RUN ./gradlew dependencies --no-daemon || return 0

# Copy the rest of the source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon

# ===== Stage 2: Run the application =====
FROM ghcr.io/spjdevops/vardr/spring-boot:java21

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port (adjust to your app's port)
EXPOSE 8080