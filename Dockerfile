# Imagen reproducible: compilación Java 21 y runtime sin privilegios.
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/target/Backend-0.0.1-SNAPSHOT.jar /app/app.jar
RUN groupadd --system monarca && useradd --system --gid monarca --uid 10001 monarca
USER monarca
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:ActiveProcessorCount=2"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
