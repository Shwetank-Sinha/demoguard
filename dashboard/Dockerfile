FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S demoguard && adduser -S -G demoguard -u 10001 demoguard
WORKDIR /app
COPY --from=backend-build --chown=10001:10001 /workspace/target/demoguard-dashboard-0.1.0-SNAPSHOT.jar /app/dashboard.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/dashboard.jar"]
