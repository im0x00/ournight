package com.ournight.api_server.domain.store.service

import com.ournight.api_server.domain.notification.event.PosReloadRequiredEvent
import com.ournight.api_server.domain.store.dao.StoreRepository
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.model.StoreTableZone
import com.ournight.api_server.domain.store.repository.StoreTableZoneRepository
import com.ournight.api_server.domain.store.view.CreateStoreTableZoneCommand
import com.ournight.api_server.domain.store.view.StoreTableView
import com.ournight.api_server.domain.store.view.StoreTableZoneView
import com.ournight.api_server.domain.store.view.StoreTableZoneWithTablesView
import com.ournight.api_server.domain.store.view.UpdateStoreTableZoneCommand
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StoreTableZoneService(
    private val storeRepository: StoreRepository,
    private val storeTableRepository: StoreTableRepository,
    private val storeTableZoneRepository: StoreTableZoneRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    fun getZones(storeId: UUID): List<StoreTableZoneView> {
        return storeTableZoneRepository.findByStoreIdOrderByDisplayOrderAsc(storeId)
            .map { it.toView() }
    }

    fun getZonesWithTables(storeId: UUID): List<StoreTableZoneWithTablesView> {
        val zones = storeTableZoneRepository.findByStoreIdOrderByDisplayOrderAsc(storeId)
        val tables = storeTableRepository.findByStoreIdOrderByTableNumberAsc(storeId)

        val grouped = tables.groupBy { it.zone?.id }

        val zoneViews = zones.map { zone ->
            StoreTableZoneWithTablesView(
                zoneId = zone.id,
                zoneName = zone.name,
                displayOrder = zone.displayOrder,
                tables = grouped[zone.id].orEmpty().map {
                    StoreTableView(
                        id = it.id!!,
                        tableNumber = it.tableNumber,
                        xPosition = it.xPosition,
                        yPosition = it.yPosition,
                        capacity = it.capacity,
                        status = it.status.name,
                        qrLink = it.qrLink,
                        qrImageUrl = it.qrImageUrl,
                        zoneId = it.zone?.id,
                        zoneName = it.zone?.name,
                    )
                }
            )
        }

        val unassignedTables = grouped[null].orEmpty()
        return if (unassignedTables.isEmpty()) {
            zoneViews
        } else {
            zoneViews + StoreTableZoneWithTablesView(
                zoneId = null,
                zoneName = "미배정",
                displayOrder = Int.MAX_VALUE,
                tables = unassignedTables.map {
                    StoreTableView(
                        id = it.id!!,
                        tableNumber = it.tableNumber,
                        xPosition = it.xPosition,
                        yPosition = it.yPosition,
                        capacity = it.capacity,
                        status = it.status.name,
                        qrLink = it.qrLink,
                        qrImageUrl = it.qrImageUrl,
                        zoneId = null,
                        zoneName = null,
                    )
                }
            )
        }
    }

    @Transactional
    fun createZone(command: CreateStoreTableZoneCommand): StoreTableZoneView {
        val store = storeRepository.findById(command.storeId)
            .orElseThrow { IllegalArgumentException("가게를 찾을 수 없습니다.") }

        val exists = storeTableZoneRepository.existsByStoreIdAndName(command.storeId, command.name)
        require(!exists) { "이미 같은 이름의 구역이 있습니다." }

        val zone = storeTableZoneRepository.save(
            StoreTableZone(
                store = store,
                name = command.name,
                displayOrder = command.displayOrder,
                isActive = true,
            )
        )

        eventPublisher.publishEvent(PosReloadRequiredEvent(command.storeId))
        return zone.toView()
    }

    @Transactional
    fun updateZone(zoneId: UUID, command: UpdateStoreTableZoneCommand): StoreTableZoneView {
        val zone = storeTableZoneRepository.findById(zoneId)
            .orElseThrow { IllegalArgumentException("테이블 구역을 찾을 수 없습니다.") }

        zone.name = command.name
        zone.displayOrder = command.displayOrder
        zone.isActive = command.isActive

        eventPublisher.publishEvent(PosReloadRequiredEvent(zone.store.id!!))
        return zone.toView()
    }

    @Transactional
    fun deleteZone(zoneId: UUID): Boolean {
        val zone = storeTableZoneRepository.findById(zoneId).orElse(null) ?: return false

        val hasAssignedTable = storeTableRepository.findByStoreIdOrderByTableNumberAsc(zone.store.id!!)
            .any { it.zone?.id == zoneId }

        require(!hasAssignedTable) { "테이블이 남아 있는 구역은 삭제할 수 없습니다." }

        storeTableZoneRepository.delete(zone)
        eventPublisher.publishEvent(PosReloadRequiredEvent(zone.store.id!!))
        return true
    }

    private fun StoreTableZone.toView(): StoreTableZoneView =
        StoreTableZoneView(
            id = this.id!!,
            name = this.name,
            displayOrder = this.displayOrder,
            isActive = this.isActive,
        )
}
