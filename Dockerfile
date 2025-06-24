# Stage 1: Build
FROM eclipse-temurin:17-jdk AS build

# Install git
RUN apt-get update && apt-get install -y git

# Set working directory
WORKDIR /app

# Copy the project files
COPY . .

# Build the application with shadowJar
RUN bin/gradle shadowJar

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy the shadow JAR from the build stage
COPY --from=build /app/app/build/libs/app-all.jar /app/app.jar

# Set RPC url here (and RPC username/password in config.yaml
ENV BITCOIN_RPC_URL="http://host.docker.internal:8332"

# Expose the application port
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "/app/app.jar"]
