package com.ournight.api_server.global.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
	val status: HttpStatus,
	val code: String,
	val message: String,
) {
	// 공통
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_001", "서버 내부 오류가 발생했습니다."),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", ""),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_003", ""),
	FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다."),


	// 무결성
	DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "DB_001", "데이터 무결성 위반"),

	// 유저
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "유저가 없습니다."),

	// 스토어
	INCORRECT_HOST_FORBIDDEN(HttpStatus.FORBIDDEN, "STORE_001", "당신은 이 팝업주점의 주인이 아닙니다."),
	DUPLICATE_TABLE_NUMBER(HttpStatus.CONFLICT, "STORE_002", "이미 등록된 테이블 번호입니다."),
	STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_003", "존재하지 않는 스토어입니다."),
	DUPLICATE_STORE_NAME(HttpStatus.CONFLICT, "STORE_004", "이미 같은 이름의 상점이 존재합니다."),
	CATEGORY_STORE_MISMATCH(HttpStatus.BAD_REQUEST, "STORE_005", "해당 카테고리는 이 스토어에 속하지 않습니다."),
	TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_006", "존재하지 않는 테이블입니다."),
	UNAUTHORIZED_STORE(HttpStatus.UNAUTHORIZED, "STORE_007", "다른 스토어 입니다."),
	TIMESLOT_INVALID(HttpStatus.NOT_FOUND, "STORE_008", "해당 시간대가 정의되지 않았습니다."),
	TABLE_ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_009", "테이블 존이 정의되지 않았습니다"),
	INVALID_TABLE_LAYOUT(HttpStatus.BAD_REQUEST, "STORE_010", "테이블 레이아웃이 올바르지 않습니다."),
	INVALID_TABLE_CAPACITY(HttpStatus.BAD_REQUEST, "STORE_011", "좌석 수는 1 이상이어야 합니다."),
	INVALID_TABLE_ID(HttpStatus.BAD_REQUEST, "STORE_012", "유효하지 않은 테이블 ID입니다."),
	TABLE_ZONE_STORE_MISMATCH(HttpStatus.BAD_REQUEST, "STORE_013", "다른 스토어의 테이블 존입니다."),
	TABLE_ZONE_INACTIVE(HttpStatus.BAD_REQUEST, "STORE_014", "비활성화된 테이블 존입니다."),
	ACTIVE_TIME_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_015", "현재 시간에 해당하는 time slot이 없어요."),
	MULTIPLE_ACTIVE_TIME_SLOTS(HttpStatus.UNAUTHORIZED, "STORE_016", "겹치는 timeslot이 존재해요. 고치세요"),
	STORE_HOST_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_017", "해당 주점의 호스트가 정의되지 않았습니다. 로직 점검 필요."),
	TIMESLOT_REFERENCED(HttpStatus.CONFLICT, "STORE_018", "패스 또는 입장 코드가 연결된 타임슬롯은 삭제할 수 없습니다."),
	TIMESLOT_EXPIRED(HttpStatus.UNAUTHORIZED, "STORE_019", "운영 시간이 끝났습니다."),

	// pass
	PASS_NOT_FOUND(HttpStatus.NOT_FOUND, "PASS_001", "입장권이 존재하지 않아요. 스탭에게 문의해주세요!"),
	PASS_NOT_ORDERABLE(HttpStatus.GONE, "PASS_002", "pass를 사용할 수 없습니다(Inactive or time over)"),
	PASS_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "PASS_003", "해당 타임슬롯에 설정된 입장 코드가 없습니다."),
	PASS_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "PASS_004", "입장 코드가 일치하지 않습니다."),
	PASS_INVALID_STATE(HttpStatus.BAD_REQUEST, "PASS_005", "패스를 활성화할 수 없는 상태입니다."),

	// table & session
	ACTIVE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_001", "활성화된 세션이 없습니다."),
	TABLE_NOT_VACANT(HttpStatus.CONFLICT, "SESSION_002", "대상 테이블이 비어 있지 않습니다."),
	TABLE_STORE_MISMATCH(HttpStatus.BAD_REQUEST, "SESSION_003", "해당 스토어의 테이블이 아닙니다."),
	FORBIDDEN_USER_IN_SESSION(HttpStatus.FORBIDDEN, "SESSION_004", "해당 유저는 세션 참가자가 아닙니다."),
	SESSION_HAS_OPEN_ORDERS(HttpStatus.CONFLICT, "SESSION_005", "현재 세션에 완료(completed or canceled)되지 못한 주문이 있어서 다른 테이블로 옮길 수 없습니다."),
	PASS_ALREADY_JOINED_OTHER_SESSION(HttpStatus.UNAUTHORIZED, "SESSION_006", "한 사용자는 여러 세션에 참가할 수 없습니다."),
	SESSION_INVALID_STATE_IN_MERGE(HttpStatus.UNAUTHORIZED, "SESSION_007", "session, sessionParticipant의 무결성이 깨졌습니다."),
	TABLE_CLEAR_NOT_ALLOWED_WITH_OPEN_ORDERS(HttpStatus.CONFLICT, "SESSION_008", "아직 진행 중인 주문이 있어요. 주문을 완료하거나 취소한 뒤 정리해주세요."),
	SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_009", "정의되지 않은 세션입니다. 없는."),
	SESSION_CHANGED(HttpStatus.CONFLICT, "SESSION_010", "테이블의 현재 이용 세션이 변경되었습니다."),
	SESSION_REQUIRES_CLEAR(HttpStatus.CONFLICT, "SESSION_011", "이전 이용 세션이 남아 있어요. 홀에서 테이블을 정리한 뒤 다시 시도해주세요."),

	// 메뉴
	CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_001", "존재하지 않는 카테고리입니다."),
	CATEGORY_NOT_EMPTY(HttpStatus.UNAUTHORIZED, "MENU_002", "카테고리 내에 아직 메뉴가 있습니다."),
	MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_003", "존재하지 않는 메뉴입니다."),

	// 카트/주문
	CART_VERSION_MISMATCH(HttpStatus.CONFLICT, "ORDER_001", "장바구니 버전이 변경되었습니다."),
	ORDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "ORDER_002", "이미 처리된 주문입니다."),
	CART_LOCKED(HttpStatus.LOCKED, "ORDER_003", "다른 요청 처리 중입니다. 잠시 후 다시 시도해주세요."),
	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "ORDER_004", "요청이 너무 많습니다."),
	OUT_OF_STOCK(HttpStatus.CONFLICT, "ORDER_005", "일부 메뉴의 재고가 부족합니다."),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_006", "존재하지 않는 주문입니다."),
	ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_007", "존재하지 않는 주문 항목입니다."),
	INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "ORDER_008", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
	ORDER_NEGATIVE_QTY(HttpStatus.UNAUTHORIZED, "ORDER_009", "특정 메뉴의 수량은 음수일 수 없습니다."),
	INVALID_ORDER_ITEM(HttpStatus.FORBIDDEN, "ORDER_010", "OrderItem의 데이터가 틀렸습니다."),


	// 호출 (Call)
	CALL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL_001", "존재하지 않는 호출 요청입니다."),
	CALL_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL_002", "존재하지 않는 호출 옵션입니다."),

	// SSE
	INVALID_TICKET(HttpStatus.UNAUTHORIZED, "SSE_001", "유효하지 않거나 만료된 티켓입니다."),
	INVALID_ROLE(HttpStatus.BAD_REQUEST, "SSE_002", "유효하지 않은 역할(Role)입니다."),


	// issue관리
	ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "ISSUE_001", "정의된 적 없는 이슈입니다."),
	INVALID_ISSUE_WITH_NO_QUANTITY(HttpStatus.FORBIDDEN, "ISSUE_002", "QTY 하나 이상이 없어서 환불금 계산을 할 수 없습니다."),

	// 대기 현황
	WAITING_STATUS_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITING_001", "존재하지 않는 대기 현황입니다.")
	;
}
