package org.im0x00.notification.reactor.redis

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.stream.StreamReceiver
import java.time.Duration

// The connection factory is Boot's auto-configured one. Pool on/off and size come from
// spring.data.redis.lettuce.pool.* (application.yml), so plain and pool are the same build.
@Configuration
class RedisStreamConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun streamReceiver(
        connectionFactory: ReactiveRedisConnectionFactory,
        @Value("\${notification.poll-timeout}") pollTimeout: Duration,
        @Value("\${notification.reader}") reader: String,
    ): StreamReceiver<String, MapRecord<String, String, String>> {
        // one line to check, before a run, which option is actually running
        val pooled = (connectionFactory as? LettuceConnectionFactory)
            ?.clientConfiguration is LettucePoolingClientConfiguration
        log.info("notification config: reader={}, pool={}, pollTimeout={}", reader, pooled, pollTimeout)

        val options = StreamReceiver.StreamReceiverOptions
            .builder()
            .pollTimeout(pollTimeout)
            .build()

        return StreamReceiver.create(
            connectionFactory,
            options,
        )
    }
}
