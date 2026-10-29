package com.ournight.api_server.domain.store.application

import com.ournight.api_server.api.host.dto.HostActiveTableSessionSummaryResponse
import com.ournight.api_server.api.host.dto.HostStoreTableResponse
import com.ournight.api_server.api.host.dto.HostStoreTableZoneWithTablesResponse
import com.ournight.api_server.domain.notification.event.PosReloadRequiredEvent
import com.ournight.api_server.domain.order.domain.Order
import com.ournight.api_server.domain.order.model.OrderItemStatus
import com.ournight.api_server.domain.order.model.OrderStatus
import com.ournight.api_server.domain.order.repository.OrderRepository
import com.ournight.api_server.domain.session.service.SessionTimeResolver
import com.ournight.api_server.domain.session.repository.TableSessionRepository
import com.ournight.api_server.domain.session.view.TableMoveView
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.domain.store.repository.StoreTableZoneRepository
import com.ournight.api_server.domain.store.view.SaveStoreTableLayoutCommand
import com.ournight.api_server.domain.store.view.StoreTableView
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StoreTableService(
	private val storeTableRepository: StoreTableRepository,
	private val storeRepository: StoreRepository,
	private val storeTableZoneRepository: StoreTableZoneRepository,
	private val tableSessionRepository: TableSessionRepository,
	private val orderRepository: OrderRepository,
	private val eventPublisher: ApplicationEventPublisher,
	private val sessionTimeResolver: SessionTimeResolver,
	private val clock: Clock,
	@Value("\${app.client.base-url}")
	private val clientBaseUrl: String,

	) {
	private data class ServingProgress(
		val servedItemCount: Int,
		val unservedItemCount: Int,
	)

	// =----------------------------- hall table 관리 ------------------------------
	private fun guestTableQrLink(storeId: UUID, tableId: UUID): String {
		return "${clientBaseUrl.trimEnd('/')}/stores/$storeId/tables/$tableId"
	}

	private fun syncQrLink(table: StoreTable) {
		val tableId = table.id ?: return
		val storeId = table.store.id ?: return
		val expected = guestTableQrLink(storeId, tableId)
		if (table.qrLink != expected) {
			table.qrLink = expected
			table.qrImageUrl = null
		}
	}

	@Transactional
	fun clearTable(tableId: UUID) {
		val table = storeTableRepository.findById(tableId)
			.orElseThrow { BusinessException(ErrorCode.TABLE_NOT_FOUND) }
		table.clear()
	}

	@Transactional
	fun moveTableState(fromTable: StoreTable, targetTable: StoreTable) {
		// 타겟 테이블이 비어있는지 다시 한번 검증 (Double Check)
		if (targetTable.status != StoreTableStatus.VACANT) {
			throw BusinessException(ErrorCode.TABLE_NOT_VACANT)
		}

		fromTable.clear()
		targetTable.occupy()
	}

	@Transactional
	fun getTableForUpdate(tableId: UUID): StoreTable {
		return storeTableRepository.findByIdWithStoreForUpdate(tableId)
			?: throw BusinessException(ErrorCode.TABLE_NOT_FOUND)
	}

	fun getTable(tableId: UUID): StoreTable {
		return storeTableRepository.findById(tableId)
			.orElseThrow { BusinessException(ErrorCode.TABLE_NOT_FOUND) }
	}

	fun validateTableBelongsToStore(table: StoreTable, storeId: UUID) {
		if (table.store.id != storeId) {
			throw BusinessException(ErrorCode.TABLE_STORE_MISMATCH)
		}
	}

	// =----------------------------- host table 정의 ------------------------------

	fun getStoreTables(storeId: UUID): List<StoreTableView> {
		return storeTableRepository
			.findByStoreIdOrderByTableNumberAsc(storeId)
			.map { it.toView() }
	}

	@Transactional
	fun saveTableLayout(command: SaveStoreTableLayoutCommand): List<StoreTableView> {
		val store = storeRepository.findById(command.storeId)
			.orElseThrow { BusinessException(ErrorCode.STORE_NOT_FOUND) }

		if (command.tables.isEmpty()) {
			val existingTables = storeTableRepository.findByStoreIdOrderByTableNumberAsc(store.id!!)
			storeTableRepository.deleteAll(existingTables)
			eventPublisher.publishEvent(PosReloadRequiredEvent(store.id!!))
			return emptyList()
		}

		val requestedTableNumbers = command.tables.map { it.tableNumber }
		if (requestedTableNumbers.size != requestedTableNumbers.toSet().size) {
			throw BusinessException(ErrorCode.DUPLICATE_TABLE_NUMBER)
		}

		if (!command.tables.all { it.capacity > 0 }) {
			throw BusinessException(ErrorCode.INVALID_TABLE_CAPACITY)
		}

		val zoneIds = command.tables.map { it.zoneId }.toSet()
		val zones = storeTableZoneRepository.findAllById(zoneIds).associateBy { it.id!! }

		if (zones.size != zoneIds.size) {
			throw BusinessException(ErrorCode.TABLE_ZONE_NOT_FOUND)
		}

		zones.values.forEach { zone ->
			if (zone.store.id != store.id) {
				throw BusinessException(ErrorCode.TABLE_ZONE_STORE_MISMATCH)
			}
			if (!zone.isActive) {
				throw BusinessException(ErrorCode.TABLE_ZONE_INACTIVE)
			}
		}

		val existingTables = storeTableRepository.findByStoreIdOrderByTableNumberAsc(store.id!!)
		val existingTableMap = existingTables.associateBy { it.id!! }

		val requestedIds = command.tables.mapNotNull { it.id }.toSet()

		require(requestedIds.size == command.tables.count { it.id != null }) {
			"중복된 테이블 id가 포함되어 있습니다."
		}

		requestedIds.forEach { requestedId ->
			require(existingTableMap.containsKey(requestedId)) {
				"해당 가게에 속하지 않는 테이블이 포함되어 있습니다."
			}
		}

		val result = mutableListOf<StoreTable>()

		command.tables.forEach { item ->
			val zone = zones[item.zoneId]
				?: throw BusinessException(ErrorCode.TABLE_ZONE_NOT_FOUND)

			val table = if (item.id == null) {
				StoreTable(
					store = store,
					tableNumber = item.tableNumber,
					xPosition = item.xPosition,
					yPosition = item.yPosition,
					capacity = item.capacity,
					qrLink = null,
					qrImageUrl = item.qrImageUrl,
					zone = zone,
				).also { newTable ->
					storeTableRepository.save(newTable)
					syncQrLink(newTable)
				}
			} else {
				val existing = existingTableMap[item.id]
					?: throw BusinessException(ErrorCode.TABLE_NOT_FOUND)

				existing.tableNumber = item.tableNumber
				existing.xPosition = item.xPosition
				existing.yPosition = item.yPosition
				existing.capacity = item.capacity
				existing.qrImageUrl = item.qrImageUrl
				existing.zone = zone
				syncQrLink(existing)
				existing
			}

			result.add(table)
		}

		val tablesToDelete = existingTables.filter { it.id!! !in requestedIds }
		if (tablesToDelete.isNotEmpty()) {
			storeTableRepository.deleteAll(tablesToDelete)
		}

		eventPublisher.publishEvent(PosReloadRequiredEvent(store.id!!))
		return result
			.sortedBy { it.tableNumber }
			.map { it.toView() }
	}

	fun getStoreTablesByZone(storeId: UUID, zoneId: UUID): List<HostStoreTableResponse> {
		val zone = storeTableZoneRepository.findById(zoneId)
			.orElseThrow { BusinessException(ErrorCode.TABLE_ZONE_NOT_FOUND) }

		require(zone.store.id == storeId) { "해당 가게의 테이블 구역이 아닙니다." }

		val tables = storeTableRepository.findByStoreIdAndZoneIdOrderByTableNumberAsc(storeId, zoneId)
		if (tables.isEmpty()) return emptyList()

		val tableIds = tables.mapNotNull { it.id }
		val sessions = tableSessionRepository.findActiveSessionsByTableIds(tableIds)
		val sessionByTableId = sessions.associateBy { it.table.id!! }

		val sessionIds = sessions.mapNotNull { it.id }
		val orders = if (sessionIds.isEmpty()) emptyList() else orderRepository.findBySessionIds(sessionIds)
		val ordersBySessionId = orders.groupBy { it.session.id!! }

		val now = LocalDateTime.now(clock)

		return tables.map { table ->
			toHostStoreTableResponse(
				table = table,
				activeSession = sessionByTableId[table.id!!],
				ordersBySessionId = ordersBySessionId,
				now = now
			)
		}
	}

	fun getStoreTablesByIds(storeId: UUID, tableIds: Collection<UUID>): List<HostStoreTableResponse> {
		if (tableIds.isEmpty()) return emptyList()

		val tables = storeTableRepository.findByStoreIdAndIdInOrderByTableNumberAsc(
			storeId = storeId,
			ids = tableIds.distinct().take(100)
		)
		if (tables.isEmpty()) return emptyList()

		val ids = tables.mapNotNull { it.id }
		val sessions = tableSessionRepository.findActiveSessionsByTableIds(ids)
		val sessionByTableId = sessions.associateBy { it.table.id!! }

		val sessionIds = sessions.mapNotNull { it.id }
		val orders = if (sessionIds.isEmpty()) emptyList() else orderRepository.findBySessionIds(sessionIds)
		val ordersBySessionId = orders.groupBy { it.session.id!! }

		val now = LocalDateTime.now(clock)

		return tables.map { table ->
			toHostStoreTableResponse(
				table = table,
				activeSession = sessionByTableId[table.id!!],
				ordersBySessionId = ordersBySessionId,
				now = now
			)
		}
	}

	fun getStoreTableZonesWithTables(storeId: UUID): List<HostStoreTableZoneWithTablesResponse> {
		if (!storeRepository.existsById(storeId)) {
			throw BusinessException(ErrorCode.STORE_NOT_FOUND)
		}

		val zones = storeTableZoneRepository.findByStoreIdOrderByDisplayOrderAsc(storeId)
		if (zones.isEmpty()) return emptyList()

		val allTables = storeTableRepository.findByStoreIdOrderByTableNumberAsc(storeId)
		val tableIds = allTables.mapNotNull { it.id }

		val sessions = if (tableIds.isEmpty()) {
			emptyList()
		} else {
			tableSessionRepository.findActiveSessionsByTableIds(tableIds)
		}
		val sessionByTableId = sessions.associateBy { it.table.id!! }

		val sessionIds = sessions.mapNotNull { it.id }
		val orders = if (sessionIds.isEmpty()) {
			emptyList()
		} else {
			orderRepository.findBySessionIds(sessionIds)
		}
		val ordersBySessionId = orders.groupBy { it.session.id!! }

		val now = LocalDateTime.now(clock)
		val tablesByZoneId = allTables.groupBy { it.zone?.id }

		return zones.map { zone ->
			val zoneTables = tablesByZoneId[zone.id].orEmpty()
				.sortedBy { it.tableNumber }
				.map { table ->
					toHostStoreTableResponse(
						table = table,
						activeSession = sessionByTableId[table.id!!],
						ordersBySessionId = ordersBySessionId,
						now = now
					)
				}

			HostStoreTableZoneWithTablesResponse(
				zoneId = zone.id,
				zoneName = zone.name,
				displayOrder = zone.displayOrder,
				tables = zoneTables
			)
		}
	}


	private fun StoreTable.toView(): StoreTableView =
		StoreTableView(
			id = this.id!!,
			tableNumber = this.tableNumber,
			xPosition = this.xPosition,
			yPosition = this.yPosition,
			capacity = this.capacity,
			status = this.status.name,
			qrLink = this.qrLink,
			qrImageUrl = this.qrImageUrl,
			zoneId = this.zone?.id,
			zoneName = this.zone?.name,
		)

	private fun toHostStoreTableResponse(
		table: StoreTable,
		activeSession: com.ournight.api_server.domain.session.model.TableSession?,
		ordersBySessionId: Map<UUID, List<Order>>,
		now: LocalDateTime
	): HostStoreTableResponse {
		val sessionSummary = activeSession?.let { session ->
			val sessionOrders = ordersBySessionId[session.id!!].orEmpty()

			val servingProgress = calculateServingProgress(sessionOrders)
			val totalAmount = sessionOrders
				.filter { it.status != OrderStatus.CANCELED }
				.fold(BigDecimal.ZERO) { acc, order -> acc + order.totalAmount }

			val sessionTime = sessionTimeResolver.resolve(session, now)

			HostActiveTableSessionSummaryResponse(
				sessionId = session.id!!,
				sessionStatus = session.status.name,
				timeOver = sessionTime.timeOver,
				enteredAt = sessionTime.enteredAt,
				servedOrderCount = servingProgress.servedItemCount,
				confirmedOrderCount = servingProgress.unservedItemCount,
				remainingMinutes = sessionTime.remainingMinutes,
				totalAmount = totalAmount
			)
		}

		return HostStoreTableResponse(
			id = table.id!!,
			tableNumber = table.tableNumber,
			capacity = table.capacity,
			status = table.status.name,
			qrLink = table.qrLink,
			qrImageUrl = table.qrImageUrl,
			zoneId = table.zone?.id,
			zoneName = table.zone?.name,
			xPosition = table.xPosition,
			yPosition = table.yPosition,
			activeSession = sessionSummary
		)
	}

	private fun calculateServingProgress(orders: List<Order>): ServingProgress {
		val activeItems = orders
			.filter { it.status != OrderStatus.CANCELED }
			.flatMap { it.orderItems }
			.filter { it.status != OrderItemStatus.CANCELED }

		val servedItemCount = activeItems
			.filter { it.status == OrderItemStatus.SERVED }
			.sumOf { it.quantity }
		val unservedItemCount = activeItems
			.filter { it.status != OrderItemStatus.SERVED }
			.sumOf { it.quantity }

		return ServingProgress(servedItemCount, unservedItemCount)
	}
}
