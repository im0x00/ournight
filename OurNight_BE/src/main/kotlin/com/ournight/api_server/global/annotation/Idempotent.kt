package com.ournight.api_server.global.annotation
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
annotation class Idempotent(
    val key: String, // SpEL을 통해 파라미터 이름을 지정 (예: "#requestId")
    val ttlSeconds: Long = 7 // 중복 방지 시간
)
