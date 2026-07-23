# syntax=docker/dockerfile:1

# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependências em camada separada (cache de rebuilds).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/gnomon-api-*.jar /app.jar

# ---- Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system gnomon && useradd --system --gid gnomon gnomon
USER gnomon

COPY --from=build /app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
