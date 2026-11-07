FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
ARG MODULE
COPY . .
RUN ./mvnw -B -pl "${MODULE}" -am package -DskipTests

FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app
COPY --from=build --chown=10001:10001 \
    /workspace/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar \
    /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
