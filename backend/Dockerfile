FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Runs as root (not a dedicated app user): the backend writes uploaded files directly into a
# bind-mounted host directory (see docker-compose.yml), whose ownership belongs to whatever
# host user created it - a non-root container user would need its uid to match that host user's,
# which isn't portable across machines. Root sidesteps that mismatch for this demo app.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
