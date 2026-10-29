package com.ournight.api_server.api.pos.controller

import com.ournight.api_server.api.pos.dto.TableClearResponse
import com.ournight.api_server.api.pos.dto.TableClearRequest
import com.ournight.api_server.api.pos.dto.TableMergeRequest
import com.ournight.api_server.api.pos.dto.TableMergeResponse
import com.ournight.api_server.api.pos.dto.TableMoveRequest
import com.ournight.api_server.api.pos.dto.TableMoveResponse
import com.ournight.api_server.api.pos.mapper.HallTableOperationMapper
import com.ournight.api_server.domain.session.service.SessionService
import com.ournight.api_server.domain.store.application.StoreTableService
import com.ournight.api_server.domain.store.model.StoreTableStatus
import com.ournight.api_server.global.error.ErrorCode
import com.ournight.api_server.global.error.exception.BusinessException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pos/{storeId}/hall/table-operations")
class HallTableOperationController(
    private val sessionService: SessionService,
) {

    @PostMapping("/{tableId}/clear")
    fun clearTable(
        @PathVariable storeId: UUID,
        @PathVariable tableId: UUID,
        @RequestBody request: TableClearRequest,
    ): TableClearResponse {
        return sessionService.clearTable(storeId, tableId, request.expectedSessionId)
    }

    @PostMapping("/{fromTableId}/move")
    fun moveTable(
        @PathVariable storeId: UUID,
        @PathVariable fromTableId: UUID,
        @RequestBody request: TableMoveRequest,
    ): TableMoveResponse {
        require(fromTableId != request.toTableId) { "같은 테이블로 이동할 수 없습니다." }

        return sessionService.moveTable(storeId, fromTableId, request.toTableId)
    }

    @PostMapping("/merge")
    fun mergeTables(
        @PathVariable storeId: UUID,
        @RequestBody request: TableMergeRequest,
    ): TableMergeResponse {
        require(request.sourceTableId != request.targetTableId) {
            "같은 테이블끼리는 합석할 수 없습니다."
        }

        return sessionService.mergeTables(storeId, request.sourceTableId, request.targetTableId)
    }
}
