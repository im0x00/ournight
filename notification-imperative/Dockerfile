FROM eclipse-temurin:25-jre
WORKDIR /app

ARG JAR_FILE=notification-imperative/build/libs/noti-mvc.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
