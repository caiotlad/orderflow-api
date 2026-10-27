FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
WORKDIR /app
COPY --from=build /workspace/target/orderflow-api-1.0.0.jar app.jar
USER orderflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
