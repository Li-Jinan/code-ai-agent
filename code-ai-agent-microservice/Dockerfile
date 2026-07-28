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

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG MODULE
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar

RUN mkdir -p /app/tmp/code_output /app/tmp/code_deploy
EXPOSE 8124 8125 8127 50051 50052 50053

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

