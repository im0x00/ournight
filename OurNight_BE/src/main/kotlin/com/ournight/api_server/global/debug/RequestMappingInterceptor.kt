package com.ournight.api_server.global.debug

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
@Profile("debug")
class RequestMappingInterceptor : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (handler is HandlerMethod) {
            log.info(
                "[HANDLER] {} {} -> {}.{}",
                request.method,
                request.requestURI,
                handler.beanType.simpleName,
                handler.method.name
            )
        }
        return true
    }
}