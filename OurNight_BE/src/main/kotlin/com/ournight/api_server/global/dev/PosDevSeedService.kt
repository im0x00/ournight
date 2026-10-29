package com.ournight.api_server.global.dev

import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.domain.OrderItem
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.repository.OrderItemRepository
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.session.model.SessionStatus
import com.ournight.api_server.domain.session.model.TableSession
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.store.dao.StoreMenuRepository
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreMenu
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.domain.StoreType
import com.ournight.api_server.domain.store.model.StoreMenuCategory
import com.ournight.api_server.domain.store.model.StoreTimeSlot
import com.ournight.api_server.domain.store.repository.StoreMenuCategoryRepository
import com.ournight.api_server.domain.store.repository.StoreTimeSlotRepository
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.domain.user.model.UserRole
import com.ournight.api_server.domain.user.repository.UserRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import org.springframework.core.env.Environment
import javax.sql.DataSource
import java.nio.file.Paths

data class PosDevSeedSummary(
    val executed: Boolean,
    val message: String,
)

@Service
class PosDevSeedService(
    private val environment: Environment,
    private val dataSource: DataSource,
    private val storeRepository: StoreRepository,
    private val storeTableRepository: StoreTableRepository,
    private val menuRepository: StoreMenuRepository,
    private val tableSessionRepository: TableSessionRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val userRepository: UserRepository,
    private val storeMenuCategoryRepository: StoreMenuCategoryRepository,
    private val storeTimeSlotRepository: StoreTimeSlotRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(PosDevSeedService::class.java)

    @Transactional
    fun seed(): PosDevSeedSummary {
        if (orderRepository.count() > 0L) {
            log.info("[PosDevSeedService] orders already exist. skip seed.")
            return PosDevSeedSummary(
                executed = false,
                message = "orders already exist. skip",
            )
        }

        val host = getSeedHost()
        val store = createStore(host)
        val slot = createTimeSlot(store)
        val category = createMenuCategory(store)

//        val table1 = createTable(store, 1)
//        val table2 = createTable(store, 2)

        val cookingMenu1 = createMenu(
            store = store,
            category = category,
            name = "김치볶음밥",
            price = 9000,
            requiresCooking = true,
            sequence = 1,
        )
        val cookingMenu2 = createMenu(
            store = store,
            category = category,
            name = "오돌뼈",
            price = 18000,
            requiresCooking = true,
            sequence = 2,
        )
        val noCookingMenu = createMenu(
            store = store,
            category = category,
            name = "소주",
            price = 5000,
            requiresCooking = false,
            sequence = 3,
        )

//        val session1 = createSession(table1, 1)
//        val session2 = createSession(table2, 1)

//        // MAIN E2E FLOW ORDER
//        // 손님 주문 생성 직후 ~ hall payment queue 시작 상태
//        val flowOrder = createOrder(
//            session = session1,
//            status = OrderStatus.PAYMENT_REQUESTED, // 필요시 PENDING_PAYMENT로 바꿔라
//            sessionOrderSequence = 1,
//        )
//        val flowItem1 = createOrderItem(flowOrder, cookingMenu1, 1, OrderItemStatus.WAITING)
//        val flowItem2 = createOrderItem(flowOrder, cookingMenu2, 1, OrderItemStatus.WAITING)
//        val flowItem3_NoCook = createOrderItem(flowOrder, noCookingMenu, 2, OrderItemStatus.WAITING)
//
//        // OPTIONAL: hall serving panel 바로 점검용
//        val servingReadyOrder = createOrder(
//            session = session2,
//            status = OrderStatus.CONFIRMED,
//            sessionOrderSequence = 1,
//        )
//        val servingReadyItem1 = createOrderItem(servingReadyOrder, cookingMenu1, 1, OrderItemStatus.COOKED)
//        val servingReadyItem2 = createOrderItem(servingReadyOrder, noCookingMenu, 2, OrderItemStatus.SERVED)

        log.info("====================================================")
        log.info("[POS DEV SEED]")
        log.info("userId={}", host.id)
        log.info("storeId={}", store.id)

        log.info("[SEED] activeProfiles={}", environment.activeProfiles.joinToString())
        log.info("[SEED] dbUrl={}", dataSource.connection.metaData.url)
        log.info("[SEED] cwd={}", Paths.get("").toAbsolutePath())
        log.info("[SEED] store count before={}", storeRepository.count())

        log.info("[SEED] about to save store")
        val savedStore = storeRepository.save(store)
        storeRepository.flush()
        log.info("[SEED] saved store id={}", savedStore.id)
        log.info("[SEED] store count after={}", storeRepository.count())
        log.info("====================================================")

        return PosDevSeedSummary(
            executed = true,
            message = "seed completed",
        )

//        // 1. [IN_PROGRESS 탭용] 1번 테이블: 1차 주문 (진행중)
//        val order1 = createOrder(session1, OrderStatus.CONFIRMED, 1)
//        createOrderItem(order1, noCookingMenu, 1, OrderItemStatus.SERVED)
//
//        // 2. [IN_PROGRESS 탭용] 1번 테이블: 2차 주문 (진행중) - "2차 주문" 표시 확인용
//        val order2 = createOrder(session1, OrderStatus.CONFIRMED, 2)
//        createOrderItem(order2, cookingMenu2, 1, OrderItemStatus.WAITING)
//
//        // 3. [DONE 탭용] 2번 테이블: 완료된 주문
//        val completedOrder = createOrder(session2, OrderStatus.COMPLETED, 1)
//        createOrderItem(completedOrder, cookingMenu1, 1, OrderItemStatus.SERVED)
//
//        // 4. [페이징 테스트용] 대량의 완료된 주문 생성 (15개)
//        // 순차적으로 생성하여 스크롤 흐름 확인
//        (3..17).forEach { i ->
//            val dummyTable = createTable(store, i + 10) // 테이블 번호 겹치지 않게
//            val dummySession = createSession(dummyTable, 1)
//            val dummyOrder = createOrder(dummySession, OrderStatus.COMPLETED, 1)
//            createOrderItem(dummyOrder, noCookingMenu, 1, OrderItemStatus.SERVED)
//        }
//
//        log.info("Hall Order UI Test Data Seeded!")
    }

    private fun getSeedHost(): User {
        val exist = userRepository.findAll().firstOrNull()
        if (exist != null) return exist

        val devUser = User(
            kakaoId = 999999,
            nickname = "DEV_HOST",
            role = UserRole.USER
        )

        return userRepository.save(devUser)
    }

    private fun createStore(host: User): Store {
        val store = Store(
            name = "POS DEV STORE",
            description = "POS 알림 및 SSE 흐름 점검용 테스트 매장",
            type = StoreType.FESTIVAL,
            host = host,
            bankName = "카카오뱅크",
            accountNumber = "3333-01-1234567",
            isActive = true,
            timeLimit = 120,
        )
        return storeRepository.save(store)
    }

    private fun createMenuCategory(store: Store): StoreMenuCategory {
        val category = StoreMenuCategory(
            store = store,
            name = "기본메뉴",
            sequence = 1,
        )
        return storeMenuCategoryRepository.save(category)
    }

    private fun createTable(store: Store, tableNo: Int): StoreTable {
        val table = StoreTable(
            store = store,
            tableNumber = tableNo,
            xPosition = tableNo * 120.0,
            yPosition = 100.0,
            capacity = 4,
            qrLink = "https://local.ournight.dev/tables/$tableNo",
        )
        return storeTableRepository.save(table)
    }

    private fun createMenu(
        store: Store,
        category: StoreMenuCategory,
        name: String,
        price: Int,
        requiresCooking: Boolean,
        sequence: Int,
    ): StoreMenu {
        val menu = StoreMenu(
            category = category,
            store = store,
            name = name,
            salePrice = BigDecimal.valueOf(price.toLong()),
            description = "$name 테스트 메뉴",
            imageUrl = null,
            sequence = sequence,
            isRecommended = false,
            isUnlimited = false,
            requiresCooking = requiresCooking,
        )
        return menuRepository.save(menu)
    }

    private fun createSession(table: StoreTable, currentOrderSequence: Int): TableSession {
        table.occupy()
        val timeSlot = storeTimeSlotRepository.findAllByStoreId(table.store.id!!).first()
        val now = LocalDateTime.now(clock)

        val session = TableSession(
            table = table,
            timeSlot = timeSlot,
            currentOrderSequence = currentOrderSequence,
            status = SessionStatus.ACTIVE,
            startedAt = now,
        )
        return tableSessionRepository.save(session)
    }

    private fun createOrder(
        session: TableSession,
        status: OrderStatus,
        sessionOrderSequence: Int,
    ): Order {
        val timeSlot = storeTimeSlotRepository.findAllByStoreId(session.table.store.id!!).first()
        val order = Order(
            session = session,
            timeSlot = timeSlot,
            status = status,
            commitId = UUID.randomUUID(),
            cartVersion = 1,
            sessionOrderSequence = sessionOrderSequence,
            orderedAt = LocalDateTime.now(clock),
        )
        return orderRepository.save(order)
    }

    private fun createOrderItem(
        order: Order,
        menu: StoreMenu,
        qty: Int,
        status: OrderItemStatus,
    ): OrderItem {
        val orderItem = OrderItem(
            order = order,
            menu = menu,
            quantity = qty,
            price = menu.salePrice,
            name = menu.name,
            status = status,
        )

        order.attachItem(orderItem)
        order.recomputeTotalAmount()

        orderItemRepository.save(orderItem)
        orderRepository.save(order)

        return orderItem
    }

    private fun createTimeSlot(store: Store): StoreTimeSlot {
        val now = LocalDateTime.now(clock)

        val slot = StoreTimeSlot(
            store = store,
            startTime = now.minusHours(1),
            endTime = now.plusHours(5),
            hasWaiting = false,
        )

        return storeTimeSlotRepository.save(slot)
    }
}
