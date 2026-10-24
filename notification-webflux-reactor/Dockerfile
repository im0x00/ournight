FROM eclipse-temurin:25-jre
WORKDIR /app

ARG JAR_FILE=notification-webflux-reactor/build/libs/webflux-reactor.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
