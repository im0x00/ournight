package com.ournight.api_server.domain.notification

import java.util.UUID

object PosStreamKey {
    fun store(storeId: UUID): String =
        "notification:store:$storeId:pos"
}