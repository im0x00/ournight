package com.ournight.api_server.global.annotation
import com.ournight.api_server.global.annotation.Idempotent
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.time.Duration

@Aspect
@Component
@Order(1)
class IdempotencyAspect(
    private val redisTemplate: StringRedisTemplate,
) {
    private val parser = SpelExpressionParser()

    @Around("@annotation(idempotent)")
    fun checkIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        // 1. SpEL을 파싱하여 requestId 값 추출
        val signature = joinPoint.signature as MethodSignature
        val context = StandardEvaluationContext()
        val args = joinPoint.args
        val parameterNames = signature.parameterNames

        for (i in args.indices) {
            context.setVariable(parameterNames[i], args[i])
        }

        val key = parser.parseExpression(idempotent.key).getValue(context, String::class.java)
            ?: throw IllegalArgumentException("Idempotency Key cannot be null")

        val redisKey = "idempotency:$key"
        val cachedValue = redisTemplate.opsForValue().get(redisKey)

        // 2. 이미 성공적으로 처리된 요청인 경우 (Retry 상황)
        if (cachedValue == "COMPLETED") {
            return ResponseEntity.ok().build<Void>()
        }

        // 3. 처리 중인 경우 (중복 클릭 등 Race Condition)
        if (cachedValue == "PROCESSING") {
            throw IllegalStateException("요청이 처리 중입니다.")
        }

        // 4. 첫 요청인 경우: PROCESSING 상태로 선점 (SETNX)
        val isFirst = redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(idempotent.ttlSeconds))

        if (isFirst != true) {
            // 0.001초 차이로 동시 진입 시 여기서 걸러짐
            throw IllegalStateException("요청이 처리 중입니다.")
        }

        try {
            // 5. 비즈니스 로직 수행
            val result = joinPoint.proceed()

            // 6. 성공 시 상태를 COMPLETED로 업데이트 (TTL 유지 또는 갱신)
            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofSeconds(idempotent.ttlSeconds))
            return result
        } catch (e: Exception) {
            redisTemplate.delete(redisKey)
            throw e
        }
    }
}