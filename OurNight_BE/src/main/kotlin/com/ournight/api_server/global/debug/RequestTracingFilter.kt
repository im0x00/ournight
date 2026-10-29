package com.ournight.api_server.global.debug

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestTracingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/actuator") ||
                uri.startsWith("/favicon.ico") ||
                uri.startsWith("/error")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.takeIf { it.isNotBlank() }
            ?.take(REQUEST_ID_MAX_LENGTH)
            ?: UUID.randomUUID().toString()
        val start = System.currentTimeMillis()

        val method = request.method
        val uri = request.requestURI

        MDC.put("requestId", requestId)
        MDC.put("httpMethod", method)
        MDC.put("httpPath", uri)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        MDC.put("eventType", "http_request_started")
        log.info("[HTTP-IN] {} {}", method, uri)
        MDC.remove("eventType")

        try {
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            MDC.put("eventType", "http_request_failed")
            MDC.put("errorType", e.javaClass.simpleName)
            log.error(
                "[HTTP-ERR] {} {} ex={}",
                method,
                uri,
                e.javaClass.simpleName,
                e
            )
            MDC.remove("eventType")
            MDC.remove("errorType")
            throw e
        } finally {
            val elapsed = System.currentTimeMillis() - start
            MDC.put("eventType", "http_request_completed")
            MDC.put("httpStatus", response.status.toString())
            MDC.put("durationMs", elapsed.toString())

            if (elapsed >= 3000) {
                MDC.put("slowRequest", "true")
                log.warn(
                    "[HTTP-OUT] {} {} status={} {}ms SLOW",
                    method,
                    uri,
                    response.status,
                    elapsed
                )
                MDC.remove("slowRequest")
            } else {
                log.info(
                    "[HTTP-OUT] {} {} status={} {}ms",
                    method,
                    uri,
                    response.status,
                    elapsed
                )
            }

            MDC.remove("requestId")
            MDC.remove("eventType")
            MDC.remove("httpMethod")
            MDC.remove("httpPath")
            MDC.remove("httpStatus")
            MDC.remove("durationMs")
        }
    }

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-Id"
        private const val REQUEST_ID_MAX_LENGTH = 128
    }
}
