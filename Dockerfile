# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy fat-jar
COPY --from=build /app/target/*-shaded.jar /app/bot.jar

VOLUME ["/data"]
CMD ["java", "-jar", "/app/bot.jar"]