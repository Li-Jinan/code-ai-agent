FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ARG MODULE
COPY pom.xml .
COPY code-ai-agent-common/pom.xml code-ai-agent-common/pom.xml
COPY code-ai-agent-model/pom.xml code-ai-agent-model/pom.xml
COPY code-ai-agent-client/pom.xml code-ai-agent-client/pom.xml
COPY code-ai-agent-ai/pom.xml code-ai-agent-ai/pom.xml
COPY code-ai-agent-user/pom.xml code-ai-agent-user/pom.xml
COPY code-ai-agent-app/pom.xml code-ai-agent-app/pom.xml
COPY code-ai-agent-screenshot/pom.xml code-ai-agent-screenshot/pom.xml
RUN mvn -B -pl ${MODULE} -am dependency:go-offline

COPY . .
RUN mvn -B -pl ${MODULE} -am package -DskipTests

FROM node:22-bookworm-slim AS node-runtime

FROM eclipse-temurin:21-jre AS service-runtime
WORKDIR /app

ARG MODULE
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/tmp/code_output /app/tmp/code_deploy /app/tmp/screenshots \
    && chown -R app:app /app

USER app
EXPOSE 8124 8125 8127 50051 50052 50053

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Generated Vue projects are built inside app-service before being published.
FROM service-runtime AS app-runtime
USER root
COPY --from=node-runtime /usr/local/ /usr/local/
USER app
