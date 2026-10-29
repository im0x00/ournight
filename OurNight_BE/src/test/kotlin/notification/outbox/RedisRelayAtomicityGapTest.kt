package notification.outbox

import com.ournight.api_server.domain.notification.PosStreamKey
import com.ournight.api_server.domain.notification.model.PosEventOutbox
import com.ournight.api_server.domain.notification.repository.PosEventOutboxRepository
import com.ournight.api_server.domain.notification.service.PosOutboxRelay
import notification.contract.OutboxObservation
import notification.contract.RedisRelayAtomicityGapContract
import notification.contract.RedisRelayAtomicityGapDriver
import notification.contract.RedisRelayRecord
import notification.contract.RelayConsumerObservation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Range
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.ToxiproxyContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest(
    classes = [RedisRelayAtomicityGapTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.data.redis.timeout=1s",
        "spring.data.redis.connect-timeout=1s",
    ],
)
class RedisRelayAtomicityGapTest : RedisRelayAtomicityGapContract() {
    @Autowired
    private lateinit var relay: PosOutboxRelay

    @Autowired
    private lateinit var outboxRepository: PosEventOutboxRepository

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    override fun createDriver(): RedisRelayAtomicityGapDriver =
        ActualRedisRelayAtomicityGapDriver(
            relay = relay,
            outboxRepository = outboxRepository,
            redisTemplate = redisTemplate,
            transactionTemplate = transactionTemplate,
            redisProxy = redisProxy,
        )

    companion object {
        private val network = Network.newNetwork()

        @Container
        @JvmStatic
        val postgres = KotlinPostgreSqlContainer(
            DockerImageName.parse("postgres:16-alpine"),
        )
            .withDatabaseName("ournight_atomicity_gap")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val redis = KotlinGenericContainer(
            DockerImageName.parse("redis:7.2-alpine"),
        )
            .withNetwork(network)
            .withNetworkAliases("relay-redis")
            .withExposedPorts(6379)

        @Container
        @JvmStatic
        val toxiproxy = ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0"),
        ).withNetwork(network)

        private lateinit var redisProxy: ToxiproxyContainer.ContainerProxy

        @DynamicPropertySource
        @JvmStatic
        fun infrastructureProperties(registry: DynamicPropertyRegistry) {
            redisProxy = toxiproxy.getProxy(redis, 6379)

            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", toxiproxy::getHost)
            registry.add("spring.data.redis.port", redisProxy::getProxyPort)
        }
    }
}

class KotlinPostgreSqlContainer(image: DockerImageName) :
    PostgreSQLContainer<KotlinPostgreSqlContainer>(image)

class KotlinGenericContainer(image: DockerImageName) :
    GenericContainer<KotlinGenericContainer>(image)

@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
    ],
)
@EntityScan(basePackageClasses = [PosEventOutbox::class])
@EnableJpaRepositories(basePackageClasses = [PosEventOutboxRepository::class])
@Import(PosOutboxRelay::class)
class RedisRelayAtomicityGapTestApplication

private class ActualRedisRelayAtomicityGapDriver(
    private val relay: PosOutboxRelay,
    private val outboxRepository: PosEventOutboxRepository,
    private val redisTemplate: StringRedisTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val redisProxy: ToxiproxyContainer.ContainerProxy,
) : RedisRelayAtomicityGapDriver {
    private val storeId = UUID.randomUUID()
    private val outboxIds = mutableSetOf<UUID>()
    private val processedOutboxIds = mutableSetOf<UUID>()
    private var domainEffectCount = 0
    private var cursor: String? = null

    init {
        restoreRedisConnection()
    }

    override fun commitOutboxEvent(): UUID {
        val event = PosEventOutbox(
            storeId = storeId,
            eventType = "TEST_EVENT",
            payload = """{"storeId":"$storeId"}""",
        )

        transactionTemplate.executeWithoutResult {
            outboxRepository.saveAndFlush(event)
        }
        outboxIds += event.id
        return event.id
    }

    override fun cutRedisConnection() {
        redisProxy.setConnectionCut(true)
    }

    override fun restoreRedisConnection() {
        redisProxy.setConnectionCut(false)

        val deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos()
        var connected = false
        while (!connected && System.nanoTime() < deadline) {
            connected = runCatching {
                redisTemplate.hasKey("relay-atomicity-gap-health")
            }.isSuccess
            if (!connected) Thread.sleep(25)
        }
        check(connected) { "Redis did not recover through Toxiproxy" }
    }

    override fun runRelay() {
        relay.publishPendingEvents()
    }

    override fun runRelayAndRollbackDatabaseTransaction() {
        transactionTemplate.executeWithoutResult { transaction ->
            relay.publishPendingEvents()
            transaction.setRollbackOnly()
        }
    }

    override fun makeRetryEligible(outboxEventId: UUID) {
        transactionTemplate.executeWithoutResult {
            val event = outboxRepository.findById(outboxEventId).orElseThrow()
            event.nextAttemptAt = Instant.EPOCH
        }
    }

    override fun observeOutbox(outboxEventId: UUID): OutboxObservation {
        val event = outboxRepository.findById(outboxEventId).orElseThrow()
        return OutboxObservation(
            published = event.publishedAt != null,
            redisStreamId = event.redisStreamId,
            attempts = event.attempts,
        )
    }

    override fun observeRedisRecords(outboxEventId: UUID): List<RedisRelayRecord> =
        allStoreRecords()
            .filter { record -> record.outboxEventId == outboxEventId }

    override fun consumeAvailableRecords() {
        allStoreRecords().forEach { record ->
            if (processedOutboxIds.add(record.outboxEventId)) {
                domainEffectCount += 1
            }
            cursor = record.streamId
        }
    }

    override fun observeConsumer(): RelayConsumerObservation =
        RelayConsumerObservation(
            domainEffectCount = domainEffectCount,
            cursor = cursor,
        )

    override fun close() {
        redisProxy.setConnectionCut(false)
        runCatching { redisTemplate.delete(PosStreamKey.store(storeId)) }
        transactionTemplate.executeWithoutResult {
            outboxIds.forEach(outboxRepository::deleteById)
        }
    }

    private fun allStoreRecords(): List<RedisRelayRecord> =
        redisTemplate.opsForStream<String, String>()
            .range(PosStreamKey.store(storeId), Range.unbounded())
            .orEmpty()
            .map { record ->
                RedisRelayRecord(
                    streamId = record.id.value,
                    outboxEventId = UUID.fromString(
                        checkNotNull(record.value["eventId"]),
                    ),
                )
            }
}
