# Multi-stage build so the image only carries a JRE + the built jar, not the whole Maven
# toolchain. Works on any container host. The current Azure deployment (docs/deployment.md)
# runs the plain jar under systemd on a VM instead, so this image is optional there - kept so
# the backend can move to a container platform without changes.
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

# Container platforms inject PORT; application.yml already reads it via ${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
