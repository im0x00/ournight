package com.ournight.api_server.global.annotation



import com.ournight.api_server.domain.store.application.StoreService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import java.util.UUID

@Aspect
@Component
class StoreOwnerAspect(
    private val storeService: StoreService
) {
    @Before("@annotation(com.ournight.api_server.global.annotation.CheckStoreOwner)")
    fun checkOwner(joinPoint: JoinPoint) {
        val args = joinPoint.args

        val signature = joinPoint.signature as MethodSignature
        val index = signature.parameterNames.indexOf("storeId")
        val storeId = joinPoint.args[index] as UUID

        val user = args.filterIsInstance<User>().firstOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED, "로그인 정보가 없음")

        // 2. 기존 서비스의 검증 로직 호출
        storeService.assertHost(storeId, user.id!!)
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckStoreOwner
