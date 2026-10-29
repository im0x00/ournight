package com.ournight.api_server.domain.order.model

enum class OrderStatus(val description: String, val step: Int) {
    // 1. 고객: 주문 생성 직후
    PENDING_PAYMENT("입금 대기 중", 1),

    // 2. 고객: '입금했어요' 버튼 클릭 시 입금 확인이 필요한 상태임
    PAYMENT_REQUESTED("입금 확인 요청", 2),

    // 3. 홀 스탭: '입금 확인/주문 확정' 버튼 클릭 시 -> 주방 노출 시작
    CONFIRMED("주문 확정(조리 대기/중)", 3),

    // 4. 시스템: 모든 하위 OrderItem이 SERVED가 되었을 때 자동 변경
    COMPLETED("서빙 완료(종료)", 4),

    // 예외: 취소
    CANCELED("취소됨", 0);

    // 주문이 주방에 보여야 하는 상태인지 체크
    fun isActiveOrder(): Boolean = this == CONFIRMED
}