package com.ournight.api_server.domain.order.model

enum class OrderItemStatus(val description: String) {
    //주문은 들어왔으나 확정되지 않음
    WAITING("입금 대기"),

    //
    /*
    초기 상태 (주문 확정이 완료되었다.)
    - requiresCooking = false 인 경우, cooking/cooked status를 건너뜀
    - requiresCooking = true 인 경우 아래의 상태를 그대로 탐
    * */
    READY("준비 대기"),

    // 주방 스탭: '조리 시작' 클릭
    COOKING("조리 중"),

    // 주방 스탭: '조리 완료' 클릭 -> 홀 스탭 서빙 대기 목록 등재
    COOKED("조리 완료"),

    // 홀 스탭: '서빙 완료' 클릭
    SERVED("서빙 완료"),

    CANCELED("취소됨")
}