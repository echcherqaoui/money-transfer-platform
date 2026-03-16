# Use JRE image for runtime
FROM eclipse-temurin:17-jre-alpine

# Set a consistent working directory
WORKDIR /app

# Create a non-privileged user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# Copy the built JAR
COPY target/*.jar app.jar

# Define the runtime execution
ENTRYPOINT ["java", "-jar", "app.jar"]