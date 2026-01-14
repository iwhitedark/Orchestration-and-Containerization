# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- runtime (small) ----------
FROM gcr.io/distroless/java17-debian12
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["java","-jar","/app/app.jar"]
