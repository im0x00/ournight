package com.ournight.api_server.domain.order.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.ournight.api_server.domain.order.model.CartItem
import com.ournight.api_server.domain.order.model.CartLockedException
import com.ournight.api_server.domain.order.model.CartMeta
import com.ournight.api_server.domain.order.model.CartSnapshot
import com.ournight.api_server.domain.order.model.CartVersionMismatchException
import com.ournight.api_server.domain.order.model.CommitStartResult
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CartRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private const val TTL_CART_SECONDS = 30L * 60L          // 1800
        private const val TTL_COMMIT_LOCK_SECONDS = 30L
        private const val TTL_COMMIT_RESULT_SECONDS = 60L
        private const val TTL_ORDER_PTR_SECONDS = 5L * 60L      // 300

        private const val FIELD_V = "v"
        private const val FIELD_ORDER_ID = "orderId"
        private const val FIELD_ORDER_STATUS = "orderStatus"
        private const val FIELD_CART_VERSION = "cartVersion"

        private const val STATUS_PENDING_PAYMENT = "PENDING_PAYMENT"

        // lua status code (repo 내부 규칙)
        private const val OK = 0L
        private const val ERR_LOCKED = 423L
        private const val ERR_CONFLICT = 409L

        private const val RATE_LIMIT_DELTA_WINDOW = 1L // 1초
        private const val RATE_LIMIT_DELTA_COUNT = 10L  // 1초에 10번 (다다닥 누르는 것 허용하되 매크로 방지)

        private const val RATE_LIMIT_COMMIT_WINDOW = 5L // 5초
        private const val RATE_LIMIT_COMMIT_COUNT = 1L  // 5초에 1번만 시도 가능 (중복 클릭 방지)
    }

    // key builder
    private fun metaKey(tableId: UUID) = "cart:meta:$tableId"
    private fun itemsKey(tableId: UUID) = "cart:items:$tableId"
    private fun commitLockKey(tableId: UUID) = "cart:commit_lock:$tableId"
    private fun commitResultKey(tableId: UUID, commitId: UUID) = "cart:commit_result:$tableId:$commitId"
    private fun orderPtrKey(tableId: UUID) = "cart:order_ptr:$tableId"
    private fun rateLimitKey(tableId: UUID, action: String) = "rate_limit:$tableId:$action"

    fun getMeta(tableId: UUID): CartMeta {
        val orderK = orderPtrKey(tableId)
        val lockK = commitLockKey(tableId)

        val vStr = redisTemplate.opsForHash<String, String>()
            .get(metaKey(tableId), FIELD_V)
        val v = vStr?.toIntOrNull() ?: 0

        val locked = redisTemplate.hasKey(lockK) == true

        val orderIdStr = redisTemplate.opsForHash<String, String>().get(orderK, FIELD_ORDER_ID)
        val orderStatus = redisTemplate.opsForHash<String, String>().get(orderK, FIELD_ORDER_STATUS)

        val orderId = orderIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        return CartMeta(
            v = v,
            locked = locked,
            orderId = orderId,
            orderStatus = orderStatus
        )
    }

    fun getSnapshotAtomic(tableId: UUID): CartSnapshot {
        val keys = listOf(
            metaKey(tableId),       // KEYS[1]
            itemsKey(tableId),      // KEYS[2]
            orderPtrKey(tableId),   // KEYS[3]
            commitLockKey(tableId)  // KEYS[4]
        )

        val res = redisTemplate.execute(GET_SNAPSHOT_SCRIPT, keys) as List<*>

        // helpers
        fun num(i: Int): Long = (res[i] as Number).toLong()
        fun str(i: Int): String = res[i] as String

        val v = num(0).toInt()
        val locked = num(1) == 1L

        val orderIdStr = str(2).ifBlank { null }
        val orderStatus = str(3).ifBlank { null }
        val count = num(4).toInt()

        val orderId = orderIdStr?.let { UUID.fromString(it) }

        val items = ArrayList<CartItem>(count)
        var idx = 5
        repeat(count) {
            val menuId = UUID.fromString(str(idx))
            val qty = (res[idx + 1] as Number).toInt()
            items.add(CartItem(menuId = menuId, qty = qty))
            idx += 2
        }

        val meta = CartMeta(
            v = v,
            locked = locked,
            orderId = orderId,
            orderStatus = orderStatus
        )

        return CartSnapshot(meta = meta, items = items)
    }

    fun applyDeltaAtomic(tableId: UUID, menuId: UUID, delta: Int): CartMeta {
        val keys = listOf(
            orderPtrKey(tableId),   // KEYS[1]
            commitLockKey(tableId), // KEYS[2]
            itemsKey(tableId),      // KEYS[3]
            metaKey(tableId)        // KEYS[4]
        )

        val res = redisTemplate.execute(
            ADD_DELTA_SCRIPT,
            keys,
            menuId.toString(),
            delta.toString(),
            TTL_CART_SECONDS.toString()
        ) as List<*>

        fun num(i: Int): Long = (res[i] as Number).toLong()
        fun str(i: Int): String = res[i] as String

        val code = num(0)
        val v = num(1).toInt()
        val locked = num(2) == 1L

        val orderIdStr = str(3).ifBlank { null }
        val orderStatus = str(4).ifBlank { null }
        val orderId = orderIdStr?.let { UUID.fromString(it) }

        val meta = CartMeta(
            v = v,
            locked = locked,
            orderId = orderId,
            orderStatus = orderStatus
        )

        return when (code) {
            OK -> meta
            ERR_LOCKED -> throw CartLockedException(meta)
            ERR_CONFLICT -> throw CartVersionMismatchException(meta)
            else -> error("unknown lua code=$code")
        }
    }

    fun commitStart(tableId: UUID, commitId: UUID, seenVersion: Int): CommitStartResult {
        val lockToken = UUID.randomUUID().toString() // 락 소유권 증명용 토큰

        val keys = listOf(
            metaKey(tableId),           // KEYS[1]
            itemsKey(tableId),          // KEYS[2]
            orderPtrKey(tableId),       // KEYS[3]
            commitLockKey(tableId),     // KEYS[4]
            commitResultKey(tableId, commitId) // KEYS[5]
        )

        val args = arrayOf(
            seenVersion.toString(),       // ARGV[1]
            lockToken,                    // ARGV[2]
            TTL_COMMIT_LOCK_SECONDS.toString(), // ARGV[3]
            TTL_CART_SECONDS.toString(),        // ARGV[4]
            STATUS_PENDING_PAYMENT        // ARGV[5]
        )

        val res = redisTemplate.execute(COMMIT_START_SCRIPT, keys, *args) as List<*>

        // Parse Result
        val code = (res[0] as Number).toLong()

        // Helper to parse Meta part (indices 1..4)
        fun parseMeta(): CartMeta {
            val v = (res[1] as Number).toInt()
            val locked = (res[2] as Number).toLong() == 1L
            val orderIdStr = res[3] as String
            val orderStatus = res[4] as String
            return CartMeta(
                v = v,
                locked = locked,
                orderId = if (orderIdStr.isBlank()) null else UUID.fromString(orderIdStr),
                orderStatus = orderStatus.ifBlank { null }
            )
        }

        return when (code) {
            OK -> { // 0: Success, snapshot follows
                val meta = parseMeta()
                val count = (res[5] as Number).toInt()
                val items = ArrayList<CartItem>(count)
                var idx = 6
                repeat(count) {
                    val menuId = UUID.fromString(res[idx] as String)
                    val qty = (res[idx + 1] as Number).toInt()
                    items.add(CartItem(menuId = menuId, qty = qty))
                    idx += 2
                }
                CommitStartResult.Success(CartSnapshot(meta, items), lockToken)
            }
            1L -> { // Already Committed (Idempotency)
                val existingOrderId = UUID.fromString(res[1] as String)
                CommitStartResult.AlreadyCommitted(existingOrderId)
            }
            ERR_LOCKED -> CommitStartResult.Locked(parseMeta()) // 423
            ERR_CONFLICT -> {
                val meta = parseMeta()
                if (meta.orderId != null && meta.orderStatus == STATUS_PENDING_PAYMENT) {
                    CommitStartResult.OrderConflict(meta)
                } else {
                    CommitStartResult.VersionConflict(meta)
                }
            }
            else -> error("Unknown commit start code: $code")
        }
    }

    fun commitFinalize(
        tableId: UUID,
        commitId: UUID,
        seenVersion: Int,
        orderId: UUID,
        lockToken: String
    ) {
        val keys = listOf(
            orderPtrKey(tableId),       // KEYS[1]
            itemsKey(tableId),          // KEYS[2]
            metaKey(tableId),           // KEYS[3]
            commitResultKey(tableId, commitId), // KEYS[4]
            commitLockKey(tableId)      // KEYS[5]
        )

        redisTemplate.execute(
            COMMIT_FINALIZE_SCRIPT,
            keys,
            orderId.toString(),           // ARGV[1]
            seenVersion.toString(),       // ARGV[2]
            lockToken,                    // ARGV[3]
            TTL_ORDER_PTR_SECONDS.toString(), // ARGV[4]
            TTL_COMMIT_RESULT_SECONDS.toString(), // ARGV[5]
            STATUS_PENDING_PAYMENT        // ARGV[6]
        )
    }

    fun commitAbort(tableId: UUID, lockToken: String) {
        // commitId는 로깅용, 실제 락 해제는 tableId와 token으로 수행
        val keys = listOf(commitLockKey(tableId))
        redisTemplate.execute(COMMIT_ABORT_SCRIPT, keys, lockToken)
    }

    fun getCommitResult(tableId: UUID, commitId: UUID): UUID? {
        val key = commitResultKey(tableId, commitId)
        val orderIdStr = redisTemplate.opsForValue().get(key)

        return orderIdStr?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
    }

    fun clearOrderPointer(tableId: UUID) {
        redisTemplate.delete(orderPtrKey(tableId))
    }

    private val GET_SNAPSHOT_SCRIPT: DefaultRedisScript<List<Any>> =
        DefaultRedisScript<List<Any>>().apply {
            setResultType(List::class.java as Class<List<Any>>)
            setScriptText(
                """
            local metaK  = KEYS[1]
            local itemsK = KEYS[2]
            local orderK = KEYS[3]
            local lockK  = KEYS[4]

            local v = tonumber(redis.call('HGET', metaK, 'v') or '0')
            local locked = (redis.call('EXISTS', lockK) == 1) and 1 or 0

            local orderId = redis.call('HGET', orderK, 'orderId') or ''
            local orderStatus = redis.call('HGET', orderK, 'orderStatus') or ''

            local arr = redis.call('HGETALL', itemsK)

            local out = { v, locked, orderId, orderStatus, 0 }
            local cnt = 0

            for i = 1, #arr, 2 do
                local menuId = arr[i]
                local qty = tonumber(arr[i + 1]) or 0
                if qty > 0 then
                    cnt = cnt + 1
                    table.insert(out, menuId)
                    table.insert(out, qty)
                end
            end

            out[5] = cnt
            return out
            """.trimIndent()
            )
        }

    private val ADD_DELTA_SCRIPT: DefaultRedisScript<List<Any>> =
        DefaultRedisScript<List<Any>>().apply {
            setResultType(List::class.java as Class<List<Any>>)
            setScriptText("""
            -- KEYS[1]=order_ptr, KEYS[2]=commit_lock, KEYS[3]=items, KEYS[4]=meta
            -- ARGV[1]=menuId, ARGV[2]=delta, ARGV[3]=ttlSeconds

            local orderK = KEYS[1]
            local lockK  = KEYS[2]
            local itemsK = KEYS[3]
            local metaK  = KEYS[4]

            -- helper: read meta fields
            local function read_meta()
              local v = tonumber(redis.call('HGET', metaK, 'v') or '0')
              local locked = (redis.call('EXISTS', lockK) == 1) and 1 or 0
              local orderId = redis.call('HGET', orderK, 'orderId') or ''
              local orderStatus = redis.call('HGET', orderK, 'orderStatus') or ''
              return v, locked, orderId, orderStatus
            end

            -- guard: if order_ptr exists and status == PENDING_PAYMENT => 409
            if redis.call('EXISTS', orderK) == 1 then
              local st = redis.call('HGET', orderK, 'orderStatus')
              if st == '${STATUS_PENDING_PAYMENT}' then
                local v, locked, orderId, orderStatus = read_meta()
                return {${ERR_CONFLICT}, v, locked, orderId, orderStatus}
              end
            end

            -- guard: if commit_lock exists => 423
            if redis.call('EXISTS', lockK) == 1 then
              local v, locked, orderId, orderStatus = read_meta()
              return {${ERR_LOCKED}, v, locked, orderId, orderStatus}
            end

            -- apply delta
            local newQty = redis.call('HINCRBY', itemsK, ARGV[1], tonumber(ARGV[2]))
            if newQty <= 0 then
              redis.call('HDEL', itemsK, ARGV[1])
            end

            -- ensure meta exists; bump v exactly once per request
            if redis.call('HEXISTS', metaK, 'v') == 0 then
              redis.call('HSET', metaK, 'v', 0)
            end
            local newV = redis.call('HINCRBY', metaK, 'v', 1)

            -- refresh TTL
            redis.call('EXPIRE', itemsK, tonumber(ARGV[3]))
            redis.call('EXPIRE', metaK, tonumber(ARGV[3]))

            -- return meta snapshot (locked should be 0 here but keep consistent)
            local locked = 0
            local orderId = redis.call('HGET', orderK, 'orderId') or ''
            local orderStatus = redis.call('HGET', orderK, 'orderStatus') or ''

            return {${OK}, newV, locked, orderId, orderStatus}
        """.trimIndent())
        }

    private val COMMIT_START_SCRIPT = DefaultRedisScript<List<Any>>().apply {
        setResultType(List::class.java as Class<List<Any>>)
        setScriptText("""
            -- KEYS: [1]meta, [2]items, [3]orderPtr, [4]lock, [5]commitResult
            -- ARGV: [1]seenVersion, [2]lockToken, [3]ttlLock, [4]ttlCart, [5]statusPending

            local metaK, itemsK, orderK, lockK, resultK = KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5]
            local seenV = tonumber(ARGV[1])
            local lockToken = ARGV[2]
            local ttlLock = tonumber(ARGV[3])
            local ttlCart = tonumber(ARGV[4])
            local statusPending = ARGV[5]

            -- 1. Fast-path: Check Commit Result (Idempotency)
            local existingOrderId = redis.call('GET', resultK)
            if existingOrderId then
                return {1, existingOrderId} -- Code 1: Already Committed
            end

            -- Helper: Read Meta
            local function read_meta()
                local v = tonumber(redis.call('HGET', metaK, 'v') or '0')
                local locked = (redis.call('EXISTS', lockK) == 1) and 1 or 0
                local orderId = redis.call('HGET', orderK, 'orderId') or ''
                local orderStatus = redis.call('HGET', orderK, 'orderStatus') or ''
                return v, locked, orderId, orderStatus
            end

            local currV, currLocked, currOrderId, currStatus = read_meta()

            -- 2. Fast-path: Check Order Ptr (Version Idempotency)
            if currOrderId ~= '' then
                local ptrVer = tonumber(redis.call('HGET', orderK, '${FIELD_CART_VERSION}') or '-1')
                if ptrVer == seenV then
                    return {1, currOrderId} -- Code 1: Treated as Already Committed
                end
                -- 3. Conflict: Pending Payment exists?
                if currStatus == statusPending then
                    return {${ERR_CONFLICT}, currV, currLocked, currOrderId, currStatus}
                end
            end

            -- 4. Check Version Match (Optimistic Lock)
            if currV ~= seenV then
                return {${ERR_CONFLICT}, currV, currLocked, currOrderId, currStatus}
            end

            -- 5. Check Lock
            if currLocked == 1 then
                return {${ERR_LOCKED}, currV, currLocked, currOrderId, currStatus}
            end

            -- 6. Acquire Lock
            local acquired = redis.call('SET', lockK, lockToken, 'NX', 'EX', ttlLock)
            if not acquired then
                -- Race condition just happened
                return {${ERR_LOCKED}, currV, 1, currOrderId, currStatus}
            end

            -- 7. Refresh TTL
            redis.call('EXPIRE', metaK, ttlCart)
            redis.call('EXPIRE', itemsK, ttlCart)

            -- 8. Build Snapshot Response (Code 0)
            -- Format: {0, v, locked(1), orderId, orderStatus, count, item1, qty1, item2, qty2...}
            local arr = redis.call('HGETALL', itemsK)
            local out = {${OK}, currV, 1, currOrderId, currStatus, 0}
            local cnt = 0

            for i = 1, #arr, 2 do
                local qty = tonumber(arr[i+1]) or 0
                if qty > 0 then
                    cnt = cnt + 1
                    table.insert(out, arr[i])   -- menuId
                    table.insert(out, qty)      -- qty
                end
            end
            out[6] = cnt
            
            return out
        """.trimIndent())
    }

    private val COMMIT_FINALIZE_SCRIPT = DefaultRedisScript<List<Any>>().apply {
        setResultType(List::class.java as Class<List<Any>>)
        setScriptText("""
            -- KEYS: [1]orderPtr, [2]items, [3]meta, [4]commitResult, [5]lock
            -- ARGV: [1]orderId, [2]ver, [3]lockToken, [4]ttlPtr, [5]ttlResult, [6]status

            local orderK, itemsK, metaK, resultK, lockK = KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5]
            local token = redis.call('GET', lockK)

            -- Security check: Only unlock if token matches
            -- 인덱스 수정됨: ARGV[4] -> ARGV[3]
            if token == ARGV[3] then
                -- 1. Create Order Pointer
                redis.call('HSET', orderK, 
                    '${FIELD_ORDER_ID}', ARGV[1], 
                    '${FIELD_CART_VERSION}', ARGV[2], 
                    '${FIELD_ORDER_STATUS}', ARGV[6] -- ARGV[7] -> ARGV[6]
                )
                redis.call('EXPIRE', orderK, tonumber(ARGV[4])) -- ARGV[5] -> ARGV[4]

                -- 2. Save Commit Result
                redis.call('SET', resultK, ARGV[1], 'EX', tonumber(ARGV[5])) -- ARGV[6] -> ARGV[5]

                -- 3. Clear Cart
                redis.call('DEL', itemsK)
                redis.call('DEL', metaK)

                -- 4. Unlock
                redis.call('DEL', lockK)
                
                return {${OK}}
            else
                return {${ERR_LOCKED}}
            end
        """.trimIndent())
    }

    private val COMMIT_ABORT_SCRIPT = DefaultRedisScript<List<Any>>().apply {
        setResultType(List::class.java as Class<List<Any>>)
        setScriptText("""
            -- KEYS: [1]lock
            -- ARGV: [1]lockToken
            
            local token = redis.call('GET', KEYS[1])
            if token == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return {1} -- Unlocked
            end
            return {0} -- Not mine or already gone
        """.trimIndent())
    }

    fun checkRateLimit(tableId: UUID, action: String) {
        val (window, limit) = when (action) {
            "commit" -> RATE_LIMIT_COMMIT_WINDOW to RATE_LIMIT_COMMIT_COUNT
            else -> RATE_LIMIT_DELTA_WINDOW to RATE_LIMIT_DELTA_COUNT // default for delta
        }

        val key = rateLimitKey(tableId, action)
        val keys = listOf(key)

        // Return: -1(통과) or 남은TTL(초과)
        val result = redisTemplate.execute(
            RATE_LIMIT_SCRIPT,
            keys,
            window.toString(),
            limit.toString()
        ) as Long

        if (result != -1L) {
            // result는 남은 TTL(초) 혹은 만료되었으면 -1, -2 등이 올 수 있음. 최소 1초 보장.
            val retryAfter = if (result < 1) 1 else result
            throw com.ournight.api_server.domain.order.model.RateLimitExceededException(retryAfter)
        }
    }

    private val RATE_LIMIT_SCRIPT = DefaultRedisScript<Long>().apply {
        setResultType(Long::class.java)
        setScriptText("""
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])

            local current = redis.call('INCR', key)

            if current == 1 then
                redis.call('EXPIRE', key, window)
            end

            if current > limit then
                return redis.call('TTL', key) -- 리밋 초과 시 남은 시간(Retry-After) 반환
            end

            return -1 -- 통과
        """.trimIndent())
    }
}
