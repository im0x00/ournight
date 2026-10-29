package com.ournight.api_server.global.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Duration

@Configuration
class RedisStreamConfig {

    @Bean(name = ["redisStreamTaskExecutor"])
    fun redisStreamTaskExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 64
        executor.maxPoolSize = 100
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("redis-stream-sub-")
        executor.initialize()
        return executor
    }

    @Bean
    fun streamMessageListenerContainer(
        redisConnectionFactory: RedisConnectionFactory,
        @Qualifier("redisStreamTaskExecutor") executor: ThreadPoolTaskExecutor
    ): StreamMessageListenerContainer<String, MapRecord<String, String, String>> {

        val options: StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> =
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofMillis(100))
                .executor(executor)
                .keySerializer(StringRedisSerializer())
                .hashKeySerializer<String, String>(StringRedisSerializer())
                .hashValueSerializer<String, String>(StringRedisSerializer())
                .build()

        return StreamMessageListenerContainer.create(redisConnectionFactory, options)
    }
}