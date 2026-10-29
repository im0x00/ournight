package com.ournight.api_server.domain.notification.service

import com.ournight.api_server.domain.notification.PosStreamKey
import java.util.UUID

data class SseMsg(
    val name: String,
    val data: String,
    val id: String?
)

fun channelKey(storeId: String): String = storeId
fun streamKey(storeId: String): String = PosStreamKey.store(UUID.fromString(storeId))