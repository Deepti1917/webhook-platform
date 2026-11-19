# ---- build ----
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
