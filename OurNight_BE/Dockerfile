# 1단계: 빌더
# Alpine(musl)+aarch64에서 Gradle libnative-platform-file-events SIGSEGV가 나므로 glibc 기반 JDK 사용
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY . .
RUN chmod +x ./gradlew && ./gradlew --no-daemon bootJar -x test

# 2단계: Jar 분해 (레이어 추출)
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /build
COPY --from=builder /build/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# 3단계: 최종 실행 이미지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG BUILD_VERSION=unknown
ARG BUILD_COMMIT=
ARG BUILD_TIME=

ENV BUILD_VERSION=${BUILD_VERSION}
ENV BUILD_COMMIT=${BUILD_COMMIT}
ENV BUILD_TIME=${BUILD_TIME}
ENV TZ=Asia/Seoul

COPY --from=extractor /build/dependencies/ ./
COPY --from=extractor /build/spring-boot-loader/ ./
COPY --from=extractor /build/snapshot-dependencies/ ./
COPY --from=extractor /build/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "org.springframework.boot.loader.launch.JarLauncher"]
