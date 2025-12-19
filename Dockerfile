# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Сначала pom — чтобы кешировать зависимости
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# Потом исходники
COPY src ./src

# Собираем и гарантированно создаём /app/bot.jar
RUN mvn -q -DskipTests package \
 && echo "----- TARGET DIR -----" \
 && ls -la target \
 && JAR="$(ls -1 target/*.jar | grep -v '^target/original-' | head -n 1)" \
 && echo "Using jar: ${JAR}" \
 && test -f "${JAR}" \
 && cp "${JAR}" /app/bot.jar \
 && ls -la /app/bot.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/bot.jar /app/bot.jar
RUN ls -la /app/bot.jar && test -s /app/bot.jar

# (по желанию) дефолты env
ENV DB_PATH=/data/bot.db \
    TZ=Europe/Moscow

VOLUME ["/data"]

ENTRYPOINT ["java", "-jar", "/app/bot.jar"]