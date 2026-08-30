FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom first (layer cache — only re-downloads deps when pom changes)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests -q

# ---- Runtime image (smaller — no JDK, just JRE) ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/bookstore-0.0.1-SNAPSHOT.jar app.jar

# Railway injects PORT at runtime; fall back to 8080 locally
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
