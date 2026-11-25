# Build context: repository root (see docker-compose.yml)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY src src
COPY example-app/build.gradle example-app/
COPY example-app/src example-app/src

# Strip CRLF line endings in case the repo was checked out on Windows
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew :example-app:bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/example-app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
