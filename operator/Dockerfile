FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S demoguard && adduser -S -G demoguard -u 10001 demoguard
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/demoguard-operator-0.1.0-SNAPSHOT.jar /app/operator.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/operator.jar"]
