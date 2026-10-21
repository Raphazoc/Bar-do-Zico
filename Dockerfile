FROM node:24-alpine AS web-build

WORKDIR /workspace/apps/web
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web/ ./
RUN npm run build

FROM maven:3.9.16-eclipse-temurin-25 AS java-build

WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/desktop/pom.xml apps/desktop/pom.xml
COPY apps/api/ apps/api/
COPY apps/desktop/ apps/desktop/
COPY --from=web-build /workspace/apps/web/dist apps/web/dist
RUN mvn -B -ntp -pl apps/api -am package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=java-build /workspace/apps/api/target/bar-api-0.1.0.jar /app/app.jar

ENV BAR_BIND=0.0.0.0
ENV BAR_PORT=8080
ENV BAR_OPEN_ACCESS=false
ENV BAR_SECURE_COOKIE=true
ENV BAR_TRAINING=false
ENV SERVER_FORWARD_HEADERS_STRATEGY=framework
ENV TZ=UTC

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
