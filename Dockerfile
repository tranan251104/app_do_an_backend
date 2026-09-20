# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (this step is cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080 (the default port for Spring Boot)
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
