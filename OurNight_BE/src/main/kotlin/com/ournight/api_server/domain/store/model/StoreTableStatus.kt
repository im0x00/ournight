package com.ournight.api_server.domain.store.model

enum class StoreTableStatus {
    VACANT,   // 비어있음 (QR 스캔 시 새 세션 시작 가능)
    OCCUPIED, // 사용 중 (주문 가능, 시간 카운트 중)
}