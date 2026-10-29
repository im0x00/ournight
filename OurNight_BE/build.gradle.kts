import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	kotlin("kapt") version "2.2.21"
	id("org.springframework.boot") version "3.2.5"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.2.21"
}

group = "com.ournight"
version = "0.0.1-SNAPSHOT"
description = "Backend service for Festival"
val springCloudVersion by extra("2023.0.0")
val jjwtVersion = "0.11.5"
val querydslDir = layout.buildDirectory.dir("generated/source/kapt/main")

extra["testcontainers.version"] = "1.20.1"

sourceSets {
	getByName("main") {
		java.srcDir(querydslDir)
		kotlin.srcDir(querydslDir)
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("net.logstash.logback:logstash-logback-encoder:7.4")
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	implementation("org.flywaydb:flyway-core")
	implementation("io.jsonwebtoken:jjwt-api:${jjwtVersion}")
	implementation("org.springframework.retry:spring-retry")
	implementation("org.springframework:spring-aspects")

	implementation("me.paulschwarz:spring-dotenv:4.0.0")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:${jjwtVersion}")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:${jjwtVersion}")

	// AWS S3 SDK
	implementation("software.amazon.awssdk:s3:2.20.0")
	implementation("software.amazon.awssdk:auth:2.20.0")

	// QR Code Generation (ZXing)
	implementation("com.google.zxing:core:3.5.3")
	implementation("com.google.zxing:javase:3.5.3")

	// QueryDSL
	implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
	kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
	
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")

	testImplementation(enforcedPlatform("org.testcontainers:testcontainers-bom:1.20.1"))
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:toxiproxy")

	testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
	testImplementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
	testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
	systemProperty("spring.profiles.active", "test")
	systemProperty("spring.main.banner-mode", "off")
	systemProperty("api.version", "1.43")
	testLogging {
		events("failed", "skipped")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
		showStandardStreams = false
	}
	environment("DOCKER_API_VERSION", "1.43")
	val dockerDesktopSocket = file("${System.getProperty("user.home")}/.docker/run/docker.sock")
	if (dockerDesktopSocket.exists()) {
		environment("DOCKER_HOST", "unix://${dockerDesktopSocket.absolutePath}")
	}
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+NestedTypeAliases"))
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.compilerOptions {
    freeCompilerArgs.add("-XXLanguage:+NestedTypeAliases")
}
