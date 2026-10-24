FROM eclipse-temurin:25-jre
WORKDIR /app

ARG JAR_FILE=notification-webflux-coroutine/build/libs/webflux-coroutine.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
