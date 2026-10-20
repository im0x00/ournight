FROM eclipse-temurin:25-jre
WORKDIR /app

ARG JAR_FILE=notification-reactive/build/libs/noti-reactive.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
