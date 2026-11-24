# Stage 1: Build the application
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY .mvn/settings.xml .mvn/settings.xml
COPY src ./src

# Reuse downloaded Maven artifacts when the build cache is available.
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B --settings .mvn/settings.xml clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Local default; Spring Boot binds to Render's PORT environment variable on deploy.
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
