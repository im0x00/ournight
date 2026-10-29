package org.im0x00.notification.coroutine

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

// Turn off the shared native connection so every Redis command borrows from ONE pool.
//
// Why: with pooling on, LettucePoolingConnectionProvider keeps a separate pool per allocation type.
//   - XREAD BLOCK (executeDedicated) borrows asynchronously   -> async pool, grows to max-active (one per store)
//   - the shared connection is fetched synchronously, once    -> a second, sync pool
// Both pools use the same min-idle, so the evictor (time-between-eviction-runs) kept min-idle idle
// connections in the sync pool that nothing used: Redis saw max-active + min-idle + 1 connections
// (pool 500 / min-idle 420 -> 922), not max-active.
// With sharing off, getReactiveConnection() builds connections from the async pool only, so
// max-active is the real upper bound again. Cost: short commands (XREVRANGE) also borrow and return.
@Configuration
class RedisConfig {
    companion object {
        // static: a BeanPostProcessor must not pull other beans in early
        @JvmStatic
        @Bean
        fun noSharedRedisConnection(): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
                if (bean is LettuceConnectionFactory) bean.setShareNativeConnection(false)
                return bean
            }
        }
    }
}
