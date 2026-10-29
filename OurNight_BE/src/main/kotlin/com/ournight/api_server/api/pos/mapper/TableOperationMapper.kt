package com.ournight.api_server.api.pos.mapper

import com.ournight.api_server.api.pos.dto.TableClearResponse
import com.ournight.api_server.api.pos.dto.TableMergeResponse
import com.ournight.api_server.api.pos.dto.TableMoveResponse
import com.ournight.api_server.domain.session.view.TableClearView
import com.ournight.api_server.domain.session.view.TableRejoinView
import com.ournight.api_server.domain.session.view.TableMoveView
import org.springframework.stereotype.Component

@Component
class HallTableOperationMapper {

    fun toClearResponse(view: TableClearView): TableClearResponse =
        TableClearResponse(
            tableId = view.tableId,
            tableNumber = view.tableNumber,
            status = view.tableStatus,
            closedSessionId = view.closedSessionId,
        )

    fun toMoveResponse(view: TableMoveView): TableMoveResponse =
        TableMoveResponse(
            sessionId = view.sessionId,
            fromTableId = view.fromTableId,
            toTableId = view.toTableId,
            fromTableStatus = view.fromTableStatus,
            toTableStatus = view.toTableStatus,
        )

    fun toMergeResponse(view: TableRejoinView): TableMergeResponse =
        TableMergeResponse(
            mergedSessionId = view.targetSessionId,
            sourceTableId = view.sourceTableId,
            targetTableId = view.targetTableId,
            targetTableStatus = view.targetTableStatus,
        )
}