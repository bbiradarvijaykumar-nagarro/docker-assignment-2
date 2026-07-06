# syntax=docker/dockerfile:1
# OPTIMIZED - see Dockerfile.before for the naive starting point and
# README.md for the size/build-time comparison between the two.

# build stage - only this layer needs the JDK + Maven, kept out of the final image
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# cache mount keeps the downloaded .m2 packages across builds instead of
# re-fetching the internet every time; separate layer so this step is skipped
# entirely when only src/ changes
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# runtime stage - just the JRE + the jar, ~150MB instead of the ~500MB+ build image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# don't run the app as root inside the container
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /build/target/docker-assignment-app.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && chown spring:spring /app/entrypoint.sh
USER spring
EXPOSE 8080
# lets "docker ps" and docker-compose show real health instead of just "running"
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["/app/entrypoint.sh"]
