# Multi-stage build so the deployed image only carries a JRE + the built jar, not the whole
# Maven toolchain. Built and pushed to Artifact Registry / deployed on Cloud Run - see
# docs/deployment.md for the exact gcloud commands.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/pinghome-backend.jar app.jar

# Cloud Run injects PORT; application.yml already reads it via ${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
