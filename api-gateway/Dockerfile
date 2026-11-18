# Build the executable Spring Boot JAR with the repository's Gradle version.
FROM gradle:9.9.1-jdk21 AS build

WORKDIR /workspace

COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean bootJar -x test \
    && set -eux; \
       JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
       test -n "$JAR_FILE"; \
       cp "$JAR_FILE" /tmp/app.jar

# Run on a smaller JRE image as a non-root user.
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app app

COPY --from=build --chown=app:app /tmp/app.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
