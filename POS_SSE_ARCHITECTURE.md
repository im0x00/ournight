# POS SSE 실시간 동기화 구조

이 문서는 POS 화면의 실시간 동기화가 기술적으로 어떻게 구축되어 있는지, 특히 SSE 연결과 복구 로직의 구현 의도를 정리한다.

## 한 줄 요약

현재 POS 실시간 시스템은 `REST 명령 + DB outbox + Redis Stream + SSE + 프론트 baseline 복구` 구조다.

SSE는 서버에서 클라이언트로 변경 이벤트를 빠르게 밀어주는 통로이고, 정합성의 최종 안전장치는 이벤트 자체가 아니라 Redis Stream ID, snapshot watermark, `/notifications/changes` 기반의 재동기화다.

## 핵심 개념 사전

이 문서에서 쓰는 용어는 다음 의미다.

| 용어 | 의미 |
| --- | --- |
| POS command | 결제 확인, 조리 완료, 서빙 완료, 테이블 이동처럼 사용자가 REST API로 요청하는 쓰기 작업 |
| Domain event | command 처리 중 서버 도메인 로직이 발행하는 내부 이벤트 |
| Outbox event | domain event를 `pos_event_outbox` 테이블에 저장한 영속 이벤트 |
| Outbox event id | outbox row의 UUID. 프론트 dedupe를 위해 payload에 `__outboxEventId`로 주입된다. |
| Redis Stream record | outbox relay가 Redis Stream에 쓴 record |
| Redis Stream ID | Redis가 record에 부여하는 `milliseconds-sequence` 형식의 ID. replay, cursor, watermark의 기준이다. |
| SSE event name | `HALL_PAYMENT`, `KITCHEN_COOK`, `RELOAD_REQUIRED` 같은 EventSource event type |
| SSE event id | 브라우저 `MessageEvent.lastEventId`로 보이는 값. Redis Stream에서 온 도메인 이벤트는 Redis Stream ID를 쓰고, `PING`, `connect`, 일부 즉시 reload 신호는 `0-0`을 쓴다. |
| Snapshot | REST API로 다시 읽는 현재 상태 묶음. 결제 대기 큐, 서빙 대기 큐, 테이블 목록, 홀 주문 목록 등이 있다. |
| Stream watermark | snapshot이 어느 Redis Stream 지점까지 커버하는지 나타내는 `X-Pos-Stream-Watermark` 헤더 값 |
| Baseline reload | 현재 POS 화면을 다시 세우기 위해 주요 snapshot을 병렬로 다시 읽는 작업 |
| Partial reload | Redis Stream 변경분을 분석해서 바뀐 order/table/menu/call만 다시 읽는 작업 |

## 보장 범위

이 구현의 보장 범위는 다음처럼 이해하는 것이 정확하다.

| 항목 | 현재 구현의 보장 |
| --- | --- |
| 서버 상태 확정 | REST command가 DB 상태를 먼저 확정한다. 실시간 이벤트는 그 결과를 전파한다. |
| 이벤트 영속성 | domain event는 outbox에 저장된 뒤 Redis Stream으로 발행된다. Redis 발행 실패는 outbox에서 재시도한다. |
| 순서 | 같은 매장의 Redis Stream 안에서는 Stream ID 순서가 복구 기준이다. 매장 간 전역 순서는 다루지 않는다. |
| 전송 보장 | 정확히 한 번 전송이 아니다. 중복 수신과 누락 가능성을 전제로 dedupe와 reload를 둔다. |
| replay | Redis Stream에 남아 있고 replay 한계 안에 있으면 `lastEventId` 이후 이벤트를 재생한다. |
| replay 실패 | stream trim, 이벤트 과다, unknown type, payload 오류 등에서는 replay를 포기하고 snapshot 복구로 전환한다. |
| 최종 정합성 | SSE 이벤트 자체가 아니라 snapshot watermark와 baseline/partial reload가 최종 정합성을 맞춘다. |

따라서 이 시스템은 “이벤트를 한 건도 잃지 않고 모든 단말에 정확히 한 번 보내는 시스템”이 아니다. POS 운영 화면에서 중요한 것은 중간 이벤트 로그를 모두 보여주는 것이 아니라, 장애 후 현재 운영 상태를 빠르게 다시 맞추는 것이다.

## 문제 상황 대비 전략 맵

이 구현은 크게 10개의 전략을 조합해서 POS 실시간 시스템의 문제 상황에 대비한다.

| 번호 | 전략 | 대비하는 문제 | 핵심 구현 |
| --- | --- | --- | --- |
| 1 | Transactional outbox | 주문 트랜잭션은 성공했는데 실시간 이벤트 발행이 실패하는 상황 | `@TransactionalEventListener(BEFORE_COMMIT)`로 `pos_event_outbox` 저장 |
| 2 | Redis Stream cursor | 클라이언트가 어디까지 이벤트를 받았는지 추적해야 하는 상황 | Stream 기반 도메인 이벤트의 Redis Stream ID를 SSE `id`, watermark, `lastEventId`로 사용 |
| 3 | Ticket 기반 SSE 연결 | EventSource가 Authorization 헤더를 다루기 어려운 상황 | 30초 TTL, 1회용 ticket을 query param으로 전달 |
| 4 | 매장 단위 fan-out | 여러 매장/여러 POS 단말의 이벤트가 섞이는 상황 | `notification:store:{storeId}:pos` stream key와 매장별 emitter/queue |
| 5 | Backpressure와 fail-fast reload | 이벤트 폭주나 느린 단말 때문에 서버 큐가 밀리는 상황 | bounded queue, overflow 시 `RELOAD_REQUIRED`, slow consumer 연결 종료 |
| 6 | Last-Event-ID replay | 네트워크가 잠깐 끊겨 중간 이벤트를 놓친 상황 | 재연결 시 `lastEventId` 이후 Redis Stream 범위 replay |
| 7 | Snapshot reconciliation | replay가 불확실하거나 이벤트만으로 상태가 부족한 상황 | `/notifications/changes`, partial reload, full baseline reload |
| 8 | Client watchdog/reconnect | 브라우저가 조용히 연결을 잃거나 sleep/resume 되는 상황 | heartbeat, 45초 watchdog, backoff+jitter, focus/online reconcile |
| 9 | Dedupe/idempotent apply | replay나 재연결 때문에 같은 이벤트가 중복 적용되는 상황 | Redis Stream ID와 `__outboxEventId` 기반 dedupe |
| 10 | Long-lived SSE resource isolation | SSE 연결이 오래 열리면서 DB connection을 붙잡는 상황 | `spring.jpa.open-in-view: false`, Redis/SSE 작업과 DB transaction 분리 |

이 전략들은 독립적으로 흩어져 있는 것이 아니라, 아래 순서로 연결되어 하나의 복구 파이프라인을 만든다.

```text
[흐름 A. REST 명령으로 서버 상태 확정]
  -> [흐름 B. 같은 트랜잭션 안에서 outbox 기록]
  -> [흐름 C. outbox relay가 Redis Stream에 publish]
  -> [흐름 D. 매장별 SSE reader가 stream event를 읽음]
  -> [흐름 E. SseEmitter가 event id와 payload를 브라우저에 전달]
  -> [흐름 F. 프론트가 Redux에 patch하고 lastAppliedEventId 저장]

정상 경로:
  -> 다음 이벤트도 같은 방식으로 patch

일시적 네트워크 유실:
  -> 프론트 재연결
  -> lastEventId 전달
  -> 서버가 Redis Stream에서 누락분 replay
  -> 프론트 dedupe 후 patch

장기 유실 / stream trim / replay 과다 / unknown event:
  -> 서버 또는 프론트가 RELOAD_REQUIRED 판단
  -> /notifications/changes로 변경 entity id 수집
  -> 변경량이 작으면 partial reload
  -> 변경량이 크거나 기준점이 없으면 full baseline reload
  -> snapshot watermark까지 lastAppliedEventId 전진

고트래픽 / 느린 단말:
  -> bounded queue와 worker shard로 흡수
  -> 한계를 넘으면 오래된 이벤트를 붙잡지 않고 RELOAD_REQUIRED
  -> slow consumer는 연결 종료
  -> 프론트가 backoff+jitter로 재연결 후 baseline 복구
```

즉 이 시스템의 핵심 메커니즘은 “이벤트를 끝까지 붙잡고 순서대로 다 보내겠다”가 아니다. 더 정확히는 “이벤트는 낮은 지연으로 전달하되, 유실/과부하/불확실성이 감지되면 stream cursor와 snapshot으로 현재 상태를 다시 맞춘다”는 구조다.

## 주요 숫자와 임계값

코드에 박혀 있는 운영상 중요한 값은 다음과 같다.

| 값 | 현재 설정 | 의미 |
| --- | --- | --- |
| ticket TTL | 30초 | ticket 발급 후 이 시간 안에 SSE subscribe를 열어야 한다. |
| `SseEmitter` timeout | 1시간 | 하나의 SSE emitter 생존 시간 |
| heartbeat | 20초 | 서버가 `PING` 이벤트를 보내는 주기 |
| 프론트 watchdog | 45초 | 이 시간 동안 SSE 활동이 없으면 프론트가 연결을 닫고 재연결한다. |
| 재연결 backoff | 2초 시작, 최대 30초 | SSE 오류 후 재연결 지연 |
| 재연결 jitter | 최대 2초 또는 delay의 30% | 여러 단말 동시 재접속 완화 |
| resume reconcile throttle | 15초 | focus/online/visibilitychange 복구 호출 최소 간격 |
| lightweight reconcile | 45초 | 주기적 변경분/baseline 확인 |
| outbox relay 주기 | 500ms | DB outbox를 Redis Stream으로 발행하는 주기 |
| outbox relay batch | 100개 | 한 번에 publish할 outbox event 수 |
| Redis Stream reader batch | 100개 | 한 번에 읽는 stream record 수 |
| Redis Stream reader block | 2초 | 새 이벤트를 기다리는 Redis blocking read 시간 |
| Redis Stream trim | 10,000개 | 매장별 stream 보관 길이 |
| replay 한계 | 300개 | 재연결 시 한 emitter에 replay할 최대 이벤트 수 |
| `/changes` 요청 limit | 500개 | 프론트가 변경분 분석 API에 요청하는 이벤트 수 |
| `/changes` 서버 허용 범위 | 1~1000개 | 컨트롤러에서 `limit`을 보정하는 범위 |
| partial reload entity 한계 | 종류별 100개 | order/table/menu/call 변경 id가 이보다 많으면 full baseline |
| 매장 broadcast queue | 2,000개 | 매장 채널 단위 이벤트 큐 |
| emitter replay queue | 500개 | 재연결 emitter별 replay 큐 |
| emitter outbound queue | 500개 | 실제 SSE 송신 직전 emitter별 큐 |
| sender worker | 16개 | 매장 채널을 shard로 나눠 drain하는 worker 수 |

## 전체 흐름

```text
POS 명령 REST API
  -> 주문/세션/콜/메뉴 도메인 상태 변경
  -> Spring domain event 발행
  -> TransactionalEventListener(BEFORE_COMMIT)
  -> pos_event_outbox 테이블 저장
  -> scheduled relay가 Redis Stream에 publish
  -> 매장별 SSE reader가 Redis Stream을 읽음
  -> SseEmitter로 브라우저에 event 전송
  -> 프론트 EventSource 수신
  -> Redux 정규화 상태에 반영
  -> 누락/오류/재연결 시 baseline 또는 변경분 재조회
```

핵심 파일:

- 백엔드 구독 API: `OurNight_BE/src/main/kotlin/com/ournight/api_server/api/pos/controller/PosNotificationController.kt`
- 이벤트 outbox 저장: `OurNight_BE/src/main/kotlin/com/ournight/api_server/domain/notification/service/PosNotificationPublisher.kt`
- Redis Stream 발행: `OurNight_BE/src/main/kotlin/com/ournight/api_server/domain/notification/service/PosOutboxRelay.kt`
- SSE 연결/전송/재생: `OurNight_BE/src/main/kotlin/com/ournight/api_server/domain/notification/service/SseNotificationService.kt`
- 변경분 수집 API: `OurNight_BE/src/main/kotlin/com/ournight/api_server/domain/notification/service/PosStreamChangeService.kt`
- 프론트 SSE 브리지: `OurNight_FE/frontend-server/src/features/pos/services/sse/PosRealtimeBridge.tsx`
- 프론트 EventSource 래퍼: `OurNight_FE/frontend-server/src/lib/realtime/sseClient.ts`
- 프론트 변경분 복구: `OurNight_FE/frontend-server/src/features/pos/usecases/queries/reloadChangedPosBaseline.ts`

## 왜 SSE인가

현재 POS의 쓰기 명령은 이미 REST API로 처리된다. 결제 확인, 조리 완료, 서빙 완료, 테이블 이동, 콜 처리 같은 조작은 모두 클라이언트가 REST로 요청하고, 서버는 그 결과를 다른 POS 화면에 알려주면 된다.

즉 이 시스템에서 필요한 실시간 통신은 대부분 `server -> client` 단방향이다. 그래서 WebSocket처럼 양방향 채널을 열기보다, 브라우저 기본 기능인 EventSource/SSE로 서버 이벤트를 내려보내는 선택이 자연스럽다.

다만 SSE는 요청 헤더에 커스텀 Authorization을 싣기 어렵다. 이 구현은 그 제약을 피하기 위해 티켓 기반 연결을 사용한다.

## 인증과 연결 수립

1. 프론트가 `/api/pos/{storeId}/notifications/ticket`을 먼저 호출한다.
2. 백엔드는 로그인 사용자와 매장 권한을 `storeService.assertHost(storeId, user.id)`로 확인한다.
3. 권한이 맞으면 Redis에 30초 TTL의 1회용 티켓을 저장한다.
4. 프론트는 `/api/pos/{storeId}/notifications/subscribe?ticket=...&connectionId=...&lastEventId=...`로 EventSource를 연다.
5. `subscribe` 엔드포인트는 Spring Security에서 `permitAll`이지만, 실제 접근 제어는 티켓 검증으로 처리한다.
6. 백엔드는 티켓을 `getAndDelete`로 꺼내므로 같은 티켓은 재사용할 수 없다.

이 구조의 의도는 EventSource의 헤더 제약을 우회하면서도, SSE URL만 알아서는 접속할 수 없게 만드는 것이다.

주의할 점은 티켓 TTL이 30초라서, 티켓 발급 후 구독까지의 시퀀스가 빠르게 이어져야 한다는 것이다. 프론트는 연결 시도마다 새 티켓을 발급받는다.

실제 서버 코드는 티켓을 조회하는 동시에 삭제한다. 이 때문에 같은 티켓으로 두 번 연결할 수 없다.

```kotlin
fun createTicket(storeId: UUID): String {
    val ticket = UUID.randomUUID().toString().replace("-", "")
    redisTemplate.opsForValue().set("ticket:$ticket", storeId.toString(), Duration.ofSeconds(30))
    return ticket
}

fun subscribe(
    expectedStoreId: UUID,
    ticket: String,
    connectionId: String?,
    lastEventId: String?
): SseEmitter {
    val storeIdStr = redisTemplate.opsForValue().getAndDelete("ticket:$ticket")
        ?: throw BusinessException(ErrorCode.INVALID_TICKET)

    if (storeIdStr != expectedStoreId.toString()) {
        throw BusinessException(ErrorCode.INVALID_TICKET)
    }

    val emitter = SseEmitter(60 * 60 * 1000L)
    ...
}
```

프론트는 EventSource 연결 전에 항상 ticket API를 호출하고, `lastEventId`가 `0-0`이 아닐 때만 구독 URL에 붙인다.

```ts
async function getPosTicket(storeId: string): Promise<string> {
    const res = await apiRequest(`/api/pos/${storeId}/notifications/ticket`, {
        method: "GET",
        credentials: "include",
    });
    ...
}

function getSseUrl(
    storeId: string,
    ticket: string,
    connectionId: string,
    lastEventId: string,
): string {
    const params = new URLSearchParams({ ticket, connectionId });
    if (lastEventId !== "0-0") params.set("lastEventId", lastEventId);
    return `/api/pos/${storeId}/notifications/subscribe?${params.toString()}`;
}
```

## 백엔드 이벤트 생산 구조

도메인 서비스에서 주문/세션/메뉴 상태가 바뀌면 Spring event가 발행된다. 예를 들면 다음 이벤트들이 있다.

- `HALL_PAYMENT`: 고객 주문 결제 확인 필요
- `HALL_PAYMENT_RESOLVED`: 결제 확인 완료
- `HALL_PAYMENT_CANCELED`: 결제 주문 취소
- `KITCHEN_COOK`: 주방 조리 요청
- `KITCHEN_COOK_RESOLVED`: 조리 완료
- `HALL_SERVE`: 홀 서빙 요청
- `HALL_SERVE_RESOLVED`: 서빙 완료
- `CUSTOMER_CALL`: 고객 호출
- `CUSTOMER_CALL_RESOLVED`: 호출 처리 완료
- `TABLE_SUMMARY_CHANGED`: 테이블 요약 변경
- `ORDERS_REASSIGNED_TO_SESSION`: 테이블 이동/합석으로 주문 세션 변경
- `KITCHEN_MENU_CHANGED`: 메뉴/재고 변경
- `RELOAD_REQUIRED`: 클라이언트 재동기화 요구

이벤트가 프론트에서 맡는 역할은 다음과 같다.

| SSE event name | 발생 의미 | 프론트 즉시 반영 | 부족할 때 보정하는 snapshot scope |
| --- | --- | --- | --- |
| `HALL_PAYMENT` | 고객 주문이 결제 확인 대기 상태로 들어옴 | 주문 entity upsert, 결제 대기 큐에 order 추가 | payments |
| `HALL_PAYMENT_RESOLVED` | 결제 확인 완료 | 주문 상태 patch, 결제 대기 큐에서 제거 | payments, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `HALL_PAYMENT_CANCELED` | 결제 대기 주문 취소 | 주문 상태를 `CANCELED`로 patch, 결제 대기 큐에서 제거 | payments, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `HALL_SERVE` | 홀에서 서빙해야 할 아이템 발생 | 주문/아이템 upsert, 서빙 큐에 item 추가 | servings, hallOrders, tables, tableOrderRounds |
| `HALL_SERVE_RESOLVED` | 서빙 완료 | 주문/아이템 위치와 상태 patch, 서빙 완료 처리 | servings, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `CUSTOMER_CALL` | 고객 호출 발생 | call entity upsert, call queue 추가 | calls |
| `CUSTOMER_CALL_RESOLVED` | 고객 호출 처리 완료 | call queue와 call entity에서 제거 | calls |
| `KITCHEN_COOK` | 주방 조리 대상 아이템 발생 | 주문/아이템 upsert | servings, hallOrders, kitchenOrders, tableOrderRounds |
| `KITCHEN_COOK_RESOLVED` | 조리 완료 | 아이템을 `COOKED`로 patch, 필요하면 서빙 큐에 추가 | servings, hallOrders, kitchenOrders, tableOrderRounds |
| `HALL_ORDER_ITEM_DECREASED` | 홀 관점에서 주문 아이템 수량 감소 | 아이템 수량/lastChange patch, 서빙 큐 재계산 | servings, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `KITCHEN_ORDER_ITEM_DECREASED` | 주방 관점에서 주문 아이템 수량 감소 | 아이템 수량/lastChange patch, 서빙 큐 재계산 | servings, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `HALL_ORDER_ITEM_CANCELED` | 홀 관점에서 주문 아이템 취소 | 아이템을 취소 상태로 patch, 서빙 큐에서 제거 | servings, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `KITCHEN_ORDER_ITEM_CANCELED` | 주방 관점에서 주문 아이템 취소 | 아이템을 취소 상태로 patch, 서빙 큐에서 제거 | servings, hallOrders, kitchenOrders, tables, tableOrderRounds |
| `TABLE_SUMMARY_CHANGED` | 테이블 상태/요약 변경 | table entity patch | tables |
| `ORDERS_REASSIGNED_TO_SESSION` | 테이블 이동/합석으로 주문의 session/table 변경 | 알고 있는 주문들의 placement patch | hallOrders, kitchenOrders, tables, tableOrderRounds |
| `KITCHEN_MENU_CHANGED` | 주방 메뉴/재고/판매 상태 변경 | menu item upsert | 없음. 필요하면 메뉴 카탈로그 reload |
| `RELOAD_REQUIRED` | 서버/클라이언트가 이벤트 기반 복구를 신뢰할 수 없음 | 주요 POS scope invalidate 후 baseline 복구 | payments, servings, calls, hallOrders, kitchenOrders, tables, tableOrderRounds |

여기서 “보정하는 snapshot scope”는 이벤트를 받은 뒤 프론트가 해당 scope를 최신 stream cursor까지 커버한 것으로 볼 수 있는 범위이자, 이벤트만으로 entity를 안정적으로 patch하지 못할 때 다시 읽어야 하는 범위다.

`PosNotificationPublisher`는 `@TransactionalEventListener(phase = BEFORE_COMMIT)`로 이벤트를 받는다. 여기서 바로 SSE를 보내지 않고 `pos_event_outbox` 테이블에 저장한다.

이 의도는 명확하다.

- DB 상태 변경과 이벤트 기록을 같은 트랜잭션 경계에 묶는다.
- SSE 전송 실패가 주문 트랜잭션을 직접 흔들지 않게 한다.
- Redis 발행 실패 시 outbox를 기준으로 재시도할 수 있게 한다.
- 이벤트 payload와 event type을 영속화해서 사후 추적이 가능하게 한다.

주문 관련 이벤트는 `isCurrentSlotOrderEvent`로 현재 활성 슬롯 또는 현재 활성 세션에 속한 주문인지 확인한 뒤 outbox에 넣는다. 과거 주문 이벤트가 현재 POS 화면에 섞이지 않게 하려는 필터다.

핵심은 도메인 이벤트를 바로 네트워크로 보내지 않는다는 점이다.

```kotlin
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
fun handleCookingRequest(event: CookingRequestedEvent) {
    if (!isCurrentSlotOrderEvent(event.storeId, event.orderId)) return
    enqueueOutbox(event.storeId, "KITCHEN_COOK", event)
}

private fun enqueueOutbox(storeId: UUID, type: String, payload: Any) {
    outboxRepository.save(
        PosEventOutbox(
            storeId = storeId,
            eventType = type,
            payload = objectMapper.writeValueAsString(payload),
        )
    )
}
```

여기서 `BEFORE_COMMIT`을 쓰는 의도는 주문 상태 변경과 outbox 기록을 같은 트랜잭션 안에 두려는 것이다. SSE 전송은 트랜잭션 밖에서 나중에 처리된다.

## Outbox와 Redis Stream

`pos_event_outbox`에는 다음 정보가 저장된다.

- `id`: outbox event id
- `store_id`
- `event_type`
- `payload`
- `created_at`
- `published_at`
- `redis_stream_id`
- `attempts`
- `next_attempt_at`
- `last_error`

`PosOutboxRelay`는 500ms마다 publish 가능한 outbox 이벤트를 최대 100개 잠금 조회한 뒤 Redis Stream에 추가한다.

Redis Stream key는 매장 단위다.

```text
notification:store:{storeId}:pos
```

Redis record에는 다음 필드가 들어간다.

- `eventId`: outbox 테이블의 event id
- `type`: SSE event name
- `payload`: JSON payload
- `timestamp`: outbox createdAt

발행 성공 시 outbox row에는 Redis Stream ID가 저장된다. 실패하면 attempts를 증가시키고 최대 60초까지 backoff를 둔다.

Stream은 최대 10,000개 길이로 trim된다. 따라서 아주 오래 끊긴 클라이언트는 이벤트 replay가 아니라 full reload로 복구해야 한다.

발행기는 500ms마다 outbox를 batch로 꺼내 Redis Stream에 넣는다.

```kotlin
private val streamMaxLen = 10_000L
private val batchSize = 100

@Scheduled(fixedDelay = 500)
@Transactional
fun publishPendingEvents() {
    val events = outboxRepository.lockPublishable(batchSize)
    if (events.isEmpty()) return

    events.forEach { event ->
        try {
            val streamId = publishToRedis(event)
            event.markPublished(streamId)
        } catch (e: Exception) {
            event.markFailed(e)
            ...
        }
    }
}
```

Redis에 쓰는 record는 이 형태다. outbox id가 `eventId`로 들어가고, Redis가 생성한 stream id는 replay/watermark 기준으로 쓰인다.

```kotlin
private fun publishToRedis(event: PosEventOutbox): String {
    val key = PosStreamKey.store(event.storeId)
    val data = mapOf(
        "eventId" to event.id.toString(),
        "type" to event.eventType,
        "payload" to event.payload,
        "timestamp" to event.createdAt.toEpochMilli().toString(),
    )

    val recordId = redisTemplate.opsForStream<String, String>()
        .add(StreamRecords.newRecord().ofMap(data).withStreamKey(key))

    touchedKeys[key] = true
    return recordId?.value ?: "0-0"
}
```

Redis 발행 실패 시 outbox row는 즉시 버려지지 않고 재시도 대기 상태가 된다. backoff는 최대 60초다.

```kotlin
fun markFailed(error: Throwable) {
    attempts += 1
    lastError = (error.message ?: error::class.java.simpleName).take(1000)
    val delaySeconds = minOf(60L, 1L shl attempts.coerceAtMost(6))
    nextAttemptAt = Instant.now().plus(Duration.ofSeconds(delaySeconds))
}
```

Stream trim은 별도 스케줄러가 담당한다.

```kotlin
@Scheduled(fixedRate = 2000)
fun trimStreams() {
    touchedKeys.keys.forEach { key ->
        redisTemplate.opsForStream<String, String>().trim(key, streamMaxLen)
        touchedKeys.remove(key)
    }
}
```

## SSE 서버 구조

`SseNotificationService`는 매장별 채널을 기준으로 emitter와 Redis reader를 관리한다.

주요 내부 구조:

- `emitters`: 매장별 연결된 `SseEmitter` 목록
- `streamReaders`: 매장별 Redis Stream reader task
- `queues`: 매장별 broadcast queue
- `replayQueues`: emitter별 replay queue
- `outboundQueues`: emitter별 실제 송신 queue
- `connectionIds`: 로그 추적용 connection id

첫 구독자가 붙으면 해당 매장의 Redis Stream reader가 시작된다. reader는 시작 시점의 stream tail부터 읽기 시작한다. 그래서 첫 연결 직전에 발생한 이벤트는 SSE reader가 직접 잡지 못할 수 있다.

이 약점을 보완하는 것이 프론트의 baseline reload다. 즉, 현재 설계는 “SSE 연결 이후 이벤트만으로 초기 상태를 만들겠다”가 아니라 “연결 후 snapshot으로 기준 상태를 잡고, 그 이후 이벤트를 적용하겠다”에 가깝다.

고트래픽을 염두에 둔 내부 구조도 있다. 매장별 큐, emitter별 큐, replay 큐가 분리되어 있고, 송신 worker는 16개 shard로 나뉜다.

```kotlin
private val queues = ConcurrentHashMap<String, ArrayBlockingQueue<SseMsg>>()
private fun queueOf(chanKey: String) = queues.computeIfAbsent(chanKey) { ArrayBlockingQueue(2000) }

private val replayQueues = ConcurrentHashMap<SseEmitter, ArrayBlockingQueue<SseMsg>>()
private fun replayQueueOf(emitter: SseEmitter) =
    replayQueues.computeIfAbsent(emitter) { ArrayBlockingQueue(500) }

private val outboundQueues = ConcurrentHashMap<SseEmitter, ArrayBlockingQueue<SseMsg>>()
private fun outboundQueueOf(emitter: SseEmitter) =
    outboundQueues.computeIfAbsent(emitter) { ArrayBlockingQueue(500) }

private val senderWorkers = 16
private val senderExec = Executors.newFixedThreadPool(senderWorkers)
private fun shard(chanKey: String) = (chanKey.hashCode() and Int.MAX_VALUE) % senderWorkers
```

Redis Stream reader는 한 번에 최대 100개를 읽고, 이벤트가 없으면 최대 2초 block한다. 이 방식은 polling 루프보다 Redis 부하가 낮고, burst가 들어올 때는 batch로 처리할 수 있다.

```kotlin
private fun readStreamLoop(chanKey: String, streamKey: String, initialLastSeenId: String) {
    var lastSeenId = initialLastSeenId
    val options = StreamReadOptions.empty()
        .count(100)
        .block(Duration.ofSeconds(2))

    while (!Thread.currentThread().isInterrupted && emitters.containsKey(chanKey)) {
        val records = redisTemplate.opsForStream<String, String>().read(
            options,
            StreamOffset.create(streamKey, ReadOffset.from(lastSeenId))
        ) ?: emptyList()

        for (record in records) {
            lastSeenId = record.id.toString()
            enqueueRecord(chanKey, streamKey, record)
        }
    }
}
```

## 재연결과 replay

프론트는 마지막으로 적용한 Redis Stream ID를 `lastAppliedEventId`로 관리한다. 재연결할 때 이 값을 `lastEventId` query param으로 보낸다.

서버는 `lastEventId`가 있으면 Redis Stream에서 `(lastEventId, +)` 범위의 이벤트를 replay한다.

다만 다음 경우에는 replay 대신 `RELOAD_REQUIRED`를 보낸다.

- `lastEventId` 형식이 잘못됨
- Redis Stream trim 때문에 gap이 만료됨
- replay 이벤트가 300개를 초과함
- 알 수 없는 event type이 있음
- replay 중 예외가 발생함

이 설계는 “끊긴 동안의 이벤트를 가능한 만큼 재생하되, 확실하지 않으면 snapshot으로 돌아간다”는 보수적 전략이다.

서버 replay 코드는 Redis Stream의 `(lastEventId, +)` 범위를 읽는다. 단, replay가 너무 많거나 gap이 만료되면 곧바로 `RELOAD_REQUIRED`로 전환한다.

```kotlin
private fun replayMissingEvents(streamKey: String, lastEventId: String, emitter: SseEmitter) {
    if (isReplayGapExpired(streamKey, lastEventId)) {
        sendReloadSignal(emitter)
        return
    }

    val range = Range.rightOpen(lastEventId, "+")
    val events = redisTemplate.opsForStream<String, String>().range(streamKey, range) ?: return
    var count = 0
    for (record in events) {
        if (count++ >= 300) {
            enqueueReplay(emitter, SseMsg("RELOAD_REQUIRED", "{}", "0-0"))
            return
        }
        ...
    }
}
```

gap 만료 판단은 Redis Stream의 첫 이벤트 ID가 클라이언트의 마지막 ID보다 뒤에 있는지를 본다. 즉, stream trim 때문에 클라이언트가 놓친 구간을 더 이상 읽을 수 없으면 replay를 포기한다.

```kotlin
private fun isReplayGapExpired(streamKey: String, lastEventId: String): Boolean {
    val lastId = parseStreamId(lastEventId) ?: return true
    val info = redisTemplate.opsForStream<String, String>().info(streamKey)
    val firstId = info.firstEntryId() ?: return false
    return compareStreamIds(parseStreamId(firstId) ?: return false, lastId) > 0
}
```

## Heartbeat와 느린 클라이언트 처리

서버는 20초마다 `PING` 이벤트를 보낸다. 프론트는 이를 실제 도메인 이벤트로 처리하지 않고 SSE activity 시간만 갱신한다.

큐가 넘치면 서버는 이벤트를 계속 쌓아두지 않는다.

- 매장 broadcast queue overflow: 큐를 비우고 `RELOAD_REQUIRED`를 넣는다.
- emitter replay queue overflow: 큐를 비우고 `RELOAD_REQUIRED`를 넣는다.
- emitter outbound queue overflow: 느린 소비자로 보고 연결을 종료한다.

이는 POS 화면에서 오래된 이벤트를 늦게 적용하는 것보다, 빠르게 재동기화하는 쪽을 선택한 설계다.

서버는 heartbeat를 모든 활성 매장 채널에 넣는다.

```kotlin
@Scheduled(fixedRate = 20000)
fun sendHeartbeat() {
    emitters.keys.forEach { chanKey ->
        enqueue(chanKey, SseMsg("PING", "{}", "0-0"))
    }
}
```

매장 broadcast queue가 꽉 차면 오래된 이벤트를 붙잡지 않고 큐를 비운 뒤 reload 신호만 남긴다.

```kotlin
private fun enqueue(chanKey: String, msg: SseMsg) {
    val q = queueOf(chanKey)
    if (q.offer(msg)) {
        markActive(chanKey)
        return
    }
    logger.warn("queue overflow: chanKey=$chanKey")
    q.clear()
    q.offer(SseMsg("RELOAD_REQUIRED", "{}", "0-0"))
    markActive(chanKey)
}
```

특정 브라우저가 느려서 outbound queue가 꽉 차면 그 연결은 slow consumer로 보고 끊는다. 코드상 `RELOAD_REQUIRED`를 큐에 넣지만 곧바로 emitter를 종료하므로, 이 신호가 브라우저에 실제 도달한다고 보장하면 안 된다. 최종 복구는 연결 종료를 감지한 프론트의 재연결과 baseline reload에 의존한다. 연결을 계속 유지하면 전체 매장 broadcast 처리에 악영향을 줄 수 있기 때문이다.

```kotlin
private fun enqueueOutbound(chanKey: String, emitter: SseEmitter, msg: SseMsg) {
    val q = outboundQueues[emitter] ?: return
    if (q.offer(msg)) return

    logger.warn("[SSE-SLOW-CONSUMER] storeId={} connectionId={} outbound queue overflow", chanKey, connectionIds[emitter])
    q.clear()
    q.offer(SseMsg("RELOAD_REQUIRED", "{}", "0-0"))
    emitter.complete()
    unregisterEmitter(chanKey, emitter, "slow-consumer")
}
```

## 프론트 연결 구조

`PosRealtimeBridge`는 `/pos/{storeId}` 하위 POS 화면에서 한 번 mount된다.

연결 흐름:

1. 현재 Redux store의 `lastAppliedEventId`를 읽는다.
2. ticket API를 호출한다.
3. ticket, connectionId, lastEventId로 SSE URL을 만든다.
4. `subscribeSse`가 native `EventSource`를 연다.
5. 각 SSE event name을 `eventAdapter`로 프론트 도메인 이벤트에 매핑한다.
6. listener middleware가 Redux 정규화 상태를 갱신한다.
7. stream event id와 outbox event id를 기준으로 dedupe한다.

프론트의 재연결 정책:

- 초기 재연결 지연: 2초
- 최대 재연결 지연: 30초
- jitter 추가
- SSE activity watchdog: 45초
- online/focus/visibilitychange 시 reconcile
- 45초마다 lightweight reconcile

연결이 열리면 프론트는 매번 baseline reload를 시도한다. 여기서 중요한 점은 “SSE open 성공”과 “상태 정합성 확보”를 별개로 본다는 것이다.

프론트의 재연결은 exponential backoff와 jitter를 같이 쓴다. 같은 시간에 여러 POS 단말이 동시에 재접속하는 상황에서 서버로 요청이 몰리는 것을 줄이려는 의도다.

```ts
const INITIAL_RECONNECT_DELAY_MS = 2_000;
const MAX_RECONNECT_DELAY_MS = 30_000;
const SSE_WATCHDOG_TIMEOUT_MS = 45_000;
const RECONCILE_ON_RESUME_THROTTLE_MS = 15_000;
const LIGHTWEIGHT_RECONCILE_INTERVAL_MS = 45_000;

const scheduleReconnect = () => {
    const baseDelayMs = reconnectDelayMsRef.current;
    const jitterMs = Math.floor(Math.random() * Math.min(2_000, baseDelayMs * 0.3));
    const delayMs = baseDelayMs + jitterMs;
    reconnectDelayMsRef.current = Math.min(baseDelayMs * 2, MAX_RECONNECT_DELAY_MS);

    reconnectTimerRef.current = window.setTimeout(() => {
        reconnectTimerRef.current = null;
        void connect();
    }, delayMs);
};
```

브라우저가 네트워크 단절을 즉시 알려주지 않는 경우도 있다. 그래서 프론트는 마지막 SSE 활동 시각을 기록하고, 45초 동안 아무 이벤트도 없으면 연결을 닫고 재연결한다.

```ts
const startWatchdog = () => {
    watchdogTimerRef.current = window.setInterval(() => {
        if (closedRef.current || !subRef.current) return;
        const idleMs = Date.now() - lastSseActivityAtRef.current;
        if (idleMs < SSE_WATCHDOG_TIMEOUT_MS) return;

        cleanupSubscription();
        scheduleReconnect();
    }, 10_000);
};
```

또한 online/focus/visibilitychange 이벤트와 45초 주기 reconcile을 통해 “브라우저가 잠들었다가 깨어난 상황”도 복구한다.

```ts
const reconcileAfterResume = () => {
    if (closedRef.current) return;
    if (document.visibilityState === "hidden") return;

    const now = Date.now();
    if (now - lastResumeReconcileAtRef.current < RECONCILE_ON_RESUME_THROTTLE_MS) return;
    lastResumeReconcileAtRef.current = now;

    reloadBaselineFnRef.current?.();
    if (!subRef.current) scheduleReconnect();
};

window.addEventListener("online", reconcileAfterResume);
window.addEventListener("focus", reconcileAfterResume);
document.addEventListener("visibilitychange", reconcileAfterResume);
```

SSE 이벤트 파싱 실패, 알 수 없는 event mapping, 서버의 `RELOAD_REQUIRED` 모두 baseline 복구로 이어진다.

```ts
const parsed = safeJson<unknown>(raw);
if (!parsed) {
    reloadBaseline();
    return;
}

const event = adaptRealtimeEvent(eventName, parsed);
if (!event) {
    reloadBaseline();
    return;
}

dispatch(dispatchPosRealtimeEventWithId(event, meta.lastEventId, dedupeEventId));

if (eventName === "RELOAD_REQUIRED") {
    reloadBaseline();
}
```

## Baseline과 변경분 복구

프론트는 이벤트 누락 가능성을 전제로 두고 있다.

`reloadChangedPosBaseline`은 다음 방식으로 동작한다.

1. `sinceEventId`가 없거나 `0-0`이면 full baseline reload를 한다.
2. `/api/pos/{storeId}/notifications/changes?since=...&limit=500`을 호출한다.
3. 백엔드는 Redis Stream을 훑어 변경된 order/table/menu/call id를 모아 반환한다.
4. 변경량이 너무 많거나 replay gap이 있으면 `requiresReload=true`를 반환한다.
5. 프론트는 변경된 엔티티만 다시 조회하거나, 필요 시 full baseline reload를 수행한다.

full baseline은 다음 snapshot들을 병렬로 다시 불러온다.

- 결제 대기 큐
- 서빙 대기 큐
- 고객 호출 큐
- 테이블 구역
- 테이블 목록
- 메뉴 카탈로그
- 홀 주문 목록
- 주방 주문 목록

이 중 stream 정합성에 직접 참여하는 snapshot 응답에는 `X-Pos-Stream-Watermark` 헤더가 포함된다. 결제 대기 큐, 서빙 대기 큐, 고객 호출 큐, 테이블 목록, 홀 주문 목록, 주방 주문 목록, 테이블 주문 라운드가 이 계열이다.

반대로 테이블 구역 목록과 메뉴 카탈로그 조회는 baseline에는 포함되지만, 현재 프론트 코드에서는 stream snapshot cursor를 전진시키는 대상으로 쓰이지 않는다. 메뉴 변경은 `KITCHEN_MENU_CHANGED` 이벤트와 필요 시 메뉴 카탈로그 reload로 보정한다.

프론트는 watermark가 있는 snapshot에 대해 “이 snapshot은 최소한 이 Redis Stream ID 시점까지의 상태를 반영한다”고 기록한다. 이후 baseline 전체가 끝나면 각 snapshot scope가 커버한 watermark 중 가장 작은 값을 기준으로 `lastAppliedEventId`를 전진시킨다. 이렇게 해야 일부 snapshot만 더 최신인 상태에서 전체 stream cursor를 너무 멀리 당겨 이벤트를 건너뛰는 일을 피할 수 있다.

`WebConfig`에서 `X-Pos-Stream-Watermark`를 CORS exposed header로 열어둔 것도 이 목적이다.

변경분 복구 API는 Redis Stream을 다시 훑되, 이벤트 자체를 모두 내려주지 않고 변경된 entity id만 모아준다. 그래서 프론트는 필요한 entity만 REST로 다시 조회할 수 있다.

```kotlin
val records = redisTemplate.opsForStream<String, String>()
    .range(streamKey, Range.rightOpen(normalizedSince, "+"))
    ?: emptyList()

if (records.size > maxEvents) {
    return reloadRequired(normalizedSince, currentWatermark, "TOO_MANY_EVENTS")
}

for (record in records) {
    val type = record.value["type"] ?: return reloadRequired(...)
    if (type == "RELOAD_REQUIRED") {
        return reloadRequired(normalizedSince, currentWatermark, "STREAM_RELOAD_REQUIRED")
    }

    val payload = parsePayload(record.value["payload"])
        ?: return reloadRequired(normalizedSince, currentWatermark, "INVALID_EVENT_PAYLOAD")

    when (type) {
        "HALL_PAYMENT",
        "HALL_PAYMENT_RESOLVED",
        "HALL_PAYMENT_CANCELED",
        "HALL_SERVE",
        "HALL_SERVE_RESOLVED",
        "KITCHEN_COOK",
        "KITCHEN_COOK_RESOLVED" -> {
            payload.uuid("orderId")?.let(orderIds::add)
            payload.uuid("tableId")?.let(tableIds::add)
            payload.uuid("menuId")?.let(menuIds::add)
        }
        ...
    }
}
```

프론트는 변경분이 너무 많으면 partial reload를 포기하고 full baseline으로 전환한다. 이 한계값은 현재 entity 종류별 100개다.

```ts
if (
    changes.changedOrderIds.length > PARTIAL_RELOAD_ENTITY_LIMIT ||
    changes.changedTableIds.length > PARTIAL_RELOAD_ENTITY_LIMIT ||
    changes.changedMenuIds.length > PARTIAL_RELOAD_ENTITY_LIMIT ||
    changes.changedCallRequestIds.length > PARTIAL_RELOAD_ENTITY_LIMIT
) {
    await dispatch(reloadPosBaseline(storeId)).unwrap();
    return { storeId, mode: "full" as const, reason: "TOO_MANY_CHANGED_ENTITIES" };
}
```

partial reload가 가능한 경우에는 변경된 id만 다시 조회한다.

```ts
await Promise.all([
    orderIds.length > 0
        ? fetchHallOrdersByIds(storeId, orderIds).then((snapshot) => {
            for (const card of snapshot.data.items) {
                applyHallOrderCard(dispatch, card);
            }
        })
        : Promise.resolve(),
    orderIds.length > 0
        ? fetchKitchenOrdersByIds(storeId, orderIds).then((snapshot) => {
            for (const card of snapshot.data.items) {
                applyKitchenOrderCard(dispatch, card);
            }
        })
        : Promise.resolve(),
    ...
]);

dispatch(posActions.markStreamWatermarkCovered({ eventId: changes.currentWatermark }));
```

## Redux 상태 반영 의도

프론트 POS store는 주문, 주문 아이템, 테이블, 콜, 메뉴를 정규화해서 저장한다.

SSE 이벤트는 대부분 전체 화면 데이터를 들고 오지 않는다. 예를 들어 조리 완료 이벤트는 특정 주문 아이템의 상태 변화에 가깝다. 그래서 listener는 다음 두 전략을 섞는다.

- 이미 알고 있는 entity면 patch로 즉시 반영한다.
- 모르는 entity거나 이벤트만으로 부족하면 관련 snapshot을 invalidate하고 reload에 맡긴다.

또한 다음 dedupe 장치가 있다.

- Redis Stream ID 기반 dedupe
- payload에 주입된 `__outboxEventId` 기반 dedupe

서버가 replay를 하거나 프론트가 reconnect를 반복해도 같은 이벤트가 중복 적용되는 위험을 줄이려는 장치다.

listener middleware는 stream id 또는 outbox id를 dedupe key로 삼는다. 이미 처리한 이벤트라면 상태 patch는 다시 하지 않고 stream cursor만 필요한 만큼 전진시킨다.

```ts
const meta = (action as { meta?: { streamEventId?: string; dedupeEventId?: string } }).meta;
const streamEventId = normalizeRedisStreamId(meta?.streamEventId);
const dedupeEventId = meta?.dedupeEventId;
const dedupeId = dedupeEventId ?? (streamEventId !== "0-0" ? `stream:${streamEventId}` : undefined);

if (dedupeId) {
    const alreadyApplied = getPosState(api).stream.recentAppliedDedupeIds.includes(dedupeId);
    if (alreadyApplied) {
        if (isReplayableStreamEvent) {
            api.dispatch(
                posActions.markStreamEventApplied({
                    eventId: streamEventId,
                    dedupeId,
                    snapshotScopes: getSnapshotScopesForRealtimeAction(action.type),
                }),
            );
        }
        return;
    }
}
```

## 네트워크 유실 상황 대응

네트워크 유실에는 전략 2, 6, 7, 8, 9가 연결된다.

| 단계 | 사용하는 전략 | 역할 |
| --- | --- | --- |
| 1 | Redis Stream cursor | Redis Stream에서 읽은 도메인 이벤트의 SSE event id에 Redis Stream ID를 넣는다. |
| 2 | Dedupe/idempotent apply | 프론트가 이벤트를 적용하면서 `lastAppliedEventId`를 갱신한다. |
| 3 | Client watchdog/reconnect | 연결 오류, idle timeout, online/focus 복귀 시 재연결한다. |
| 4 | Last-Event-ID replay | 재연결 URL에 `lastEventId`를 실어 누락 구간 replay를 요청한다. |
| 5 | Snapshot reconciliation | replay가 불가능하면 변경분 reload 또는 full baseline으로 전환한다. |

연결 메커니즘은 다음과 같다.

```text
SSE 정상 수신
  -> MessageEvent.lastEventId 확인
  -> Redux listener가 patch + lastAppliedEventId 저장
  -> 네트워크 단절
  -> watchdog/onerror/online/focus가 reconnect 예약
  -> 새 ticket 발급
  -> subscribe URL에 lastEventId 포함
  -> 서버가 Redis Stream range(lastEventId, +) replay
  -> replay 가능: 누락 이벤트를 다시 dispatch
  -> replay 불가: RELOAD_REQUIRED
  -> 프론트가 /notifications/changes 또는 full baseline 실행
```

이 구조에서 중요한 점은 네트워크 유실을 “예외”로 보지 않는다는 것이다. POS 단말은 와이파이, 태블릿 sleep, 브라우저 background 상태의 영향을 자주 받기 때문에, 유실 후 재동기화가 기본 경로로 들어가 있다.

서버 전송부는 SSE event id를 명시적으로 넣는다. Redis Stream에서 읽은 도메인 이벤트는 `id`가 Redis Stream ID이고, `PING`, `connect`, 일부 즉시 reload 신호처럼 stream record가 아닌 제어 이벤트는 `0-0`을 쓴다.

```kotlin
private fun sendToEmitter(emitter: SseEmitter, name: String, data: String, id: String): Boolean {
    emitter.send(
        SseEmitter.event()
            .id(id)
            .name(name)
            .data(data)
            .reconnectTime(2000)
    )
    return true
}
```

프론트 EventSource 래퍼는 브라우저의 `MessageEvent.lastEventId`를 handler로 넘긴다.

```ts
es.addEventListener(eventName, (ev) => {
    const message = ev as MessageEvent;
    handler(message.data, { lastEventId: message.lastEventId });
});
```

프론트가 연결을 다시 만들 때는 마지막 적용 지점을 URL에 싣는다.

```ts
const url = getSseUrl(storeId, ticket, connectionId, lastAppliedEventIdRef.current);
```

그래도 복구가 불가능한 상황은 full baseline으로 떨어진다.

| full baseline으로 전환되는 조건 | 이유 |
| --- | --- |
| Redis Stream이 trim되어 gap이 사라진 경우 | `lastEventId` 이후 이벤트를 완전히 재생할 수 없다. |
| replay 이벤트가 300개를 넘는 경우 | 단일 연결 replay가 너무 커져 SSE 송신 큐를 압박할 수 있다. |
| `/notifications/changes` 결과가 500개를 넘는 경우 | 변경분 계산 자체가 너무 커져 partial reload의 장점이 줄어든다. |
| payload parse 실패 | 이벤트 의미를 신뢰할 수 없다. |
| unknown event type | 프론트/백엔드 이벤트 스키마가 어긋났을 가능성이 있다. |
| SSE queue overflow | 중간 이벤트를 모두 유지하지 못했으므로 cursor만 믿을 수 없다. |
| slow consumer | 해당 단말이 이벤트 소비 속도를 따라오지 못한다. |
| baseline partial reload 대상 entity가 너무 많은 경우 | 개별 재조회보다 전체 snapshot이 더 안정적이다. |

이 점에서 현재 구현은 “유실을 절대 막는다”가 아니라 “유실을 감지하면 빠르게 기준 상태를 다시 잡는다”는 설계다.

## 고트래픽 상황 대응

고트래픽에는 전략 1, 4, 5, 7, 8, 10이 연결된다.

| 단계 | 사용하는 전략 | 역할 |
| --- | --- | --- |
| 1 | Transactional outbox | 주문 처리 트랜잭션과 실시간 전송을 분리한다. |
| 2 | 매장 단위 fan-out | 매장별 Redis Stream과 queue로 이벤트 범위를 나눈다. |
| 3 | Backpressure와 fail-fast reload | queue가 밀리면 오래된 이벤트를 고집하지 않고 reload로 전환한다. |
| 4 | Snapshot reconciliation | 이벤트 폭주 후 현재 상태를 snapshot으로 재정렬한다. |
| 5 | Client watchdog/reconnect | 여러 단말이 동시에 재접속할 때 backoff+jitter로 요청 몰림을 줄인다. |
| 6 | Long-lived SSE resource isolation | SSE가 길게 열려도 JPA connection을 붙잡지 않게 한다. |

연결 메커니즘은 다음과 같다.

```text
주문/테이블/콜 이벤트 폭주
  -> 도메인 트랜잭션은 outbox 저장까지만 수행
  -> relay가 500ms마다 최대 100개씩 Redis Stream publish
  -> 매장별 stream reader가 count=100 batch read
  -> sender worker shard가 매장 queue를 drain
  -> 단말별 outbound queue로 전달

처리량 안쪽:
  -> 각 단말이 SSE 이벤트를 받고 Redux patch

처리량 초과:
  -> broadcast/replay queue overflow
  -> RELOAD_REQUIRED로 전환
  -> outbound queue overflow 단말은 slow consumer로 연결 종료
  -> 프론트 재연결 후 changes/full baseline으로 현재 상태 복구
```

첫째, 도메인 트랜잭션과 실시간 전송을 분리한다. 주문 처리 요청은 outbox 저장까지만 트랜잭션에 묶이고, Redis/SSE 발행은 스케줄러가 별도로 처리한다. Redis나 SSE가 느려도 주문 트랜잭션이 직접 장시간 붙잡히지 않는다.

둘째, Redis Stream과 서버 큐에 batch/backpressure 전략이 있다.

- outbox relay는 500ms마다 최대 100개 batch publish
- Redis Stream reader는 한 번에 최대 100개 read
- 매장 broadcast queue는 2,000개
- emitter replay queue는 500개
- emitter outbound queue는 500개
- sender worker는 16개 shard
- Redis Stream은 매장별 key로 분산

셋째, SSE처럼 오래 열리는 HTTP 연결이 DB connection을 잡고 있지 않도록 JPA Open Session in View를 끈다.

```yaml
spring:
  jpa:
    open-in-view: false # 필수! 없으면 SSE 연결시 connectionpool고갈됨
```

넷째, 임계치를 넘으면 “더 오래 버티기”보다 “재동기화”를 선택한다.

- broadcast queue overflow -> 큐 clear 후 `RELOAD_REQUIRED`
- replay queue overflow -> 큐 clear 후 `RELOAD_REQUIRED`
- outbound queue overflow -> slow consumer 연결 종료
- replay 300개 초과 -> `RELOAD_REQUIRED`
- changes 500개 초과 -> full baseline
- changed entity 종류별 100개 초과 -> full baseline

이 방식은 POS에서 중요한 것이 모든 중간 이벤트를 화면에 순서대로 보여주는 것이 아니라, 최종 운영 상태를 빨리 맞추는 것이라는 판단에 가깝다. 예를 들어 결제 요청 200개를 늦게 하나씩 적용하기보다, 현재 결제 대기 큐 snapshot을 다시 받아 화면을 맞추는 편이 낫다.

다만 약점도 있다. 고트래픽 시간대에 full baseline이 여러 단말에서 동시에 발생하면 REST snapshot API에 부하가 몰릴 수 있다. 프론트가 reconnect jitter와 resume throttle을 두고 있긴 하지만, 서버 측에서 full baseline API의 캐싱, pagination, rate limit, 매장 단위 coalescing이 더해지면 더 안정적일 것이다.

## 구현 의도 정리

이 시스템은 정확히 한 번 전송을 보장하는 구조가 아니다. 의도는 다음에 가깝다.

- REST 명령으로 서버 상태를 먼저 확정한다.
- 변경 사실은 outbox에 남겨 Redis Stream으로 fan-out한다.
- SSE는 낮은 지연으로 POS 화면을 갱신한다.
- 네트워크 끊김, 서버 재시작, queue overflow, stream trim은 정상 시나리오로 보고 reload/reconcile로 회복한다.
- 이벤트는 빠른 UI 반영 수단이고, snapshot은 최종 정합성 회복 수단이다.

따라서 이 구조를 이해할 때 “SSE 이벤트가 모든 상태를 완전하게 전달한다”고 보면 안 된다. 더 정확한 해석은 “SSE는 변경 알림과 일부 patch를 제공하고, snapshot API가 상태의 기준점을 다시 잡는다”이다.

## 기술적 장점

- EventSource 기반이라 브라우저 구현이 단순하다.
- REST 명령과 실시간 알림의 책임이 분리되어 있다.
- outbox 덕분에 DB 트랜잭션과 이벤트 발행의 결합도가 낮다.
- Redis Stream ID를 기준으로 replay와 변경분 수집이 가능하다.
- 매장별 stream key라 POS 화면 간 이벤트 범위가 명확하다.
- watermark 기반으로 snapshot과 stream의 시점을 맞추려는 장치가 있다.
- queue overflow나 replay gap을 full reload로 회복하는 실패 전략이 있다.

## 주의할 점과 개선 포인트

1. SSE 첫 reader는 현재 stream tail에서 시작한다. 그래서 최초 연결 시 baseline reload는 선택이 아니라 필수에 가깝다.

2. Redis Stream은 10,000개로 trim된다. 매장 이벤트가 많은 시간대에는 오래 끊긴 클라이언트가 replay하지 못하고 full reload로 떨어지는 것이 정상이다.

3. `subscribe`는 Spring Security에서 permitAll이므로 티켓 검증이 보안 경계다. 티켓 TTL, one-time 사용, storeId 검증은 깨지면 안 된다.

4. SSE 쪽 ticket/changes API는 `assertHost`를 사용하지만, POS의 다른 명령 API 전체가 동일한 수준으로 매장 권한을 검증하는지는 별도로 점검해야 한다. 실시간 연결만 안전해도 REST 명령 권한이 느슨하면 시스템 전체 보안은 약해진다.

5. 이벤트 payload는 partial update 중심이다. 새 화면을 만들 때 SSE 이벤트만 보고 UI를 완성하려 하면 안 되고, 모르는 entity는 snapshot reload로 복구하는 패턴을 따라야 한다.

6. 서버의 queue overflow와 slow consumer 처리는 과감하게 reload를 요구한다. POS UX에서는 일시적으로 화면이 재조회되는 상태를 자연스럽게 보여줄 필요가 있다.

7. SSE/replay/outbox는 장애 시나리오가 많은 영역인데, 현재 코드 기준으로는 이 설계 의도를 검증하는 테스트를 충분히 보강할 필요가 있다.

8. 프론트 loader의 `force` 의미가 일관되지는 않다. `loadPaymentQueue`, `loadServingQueue`, `loadCallQueue`는 `force` 인자를 받을 수 있지만 stale snapshot guard를 우회하지 않는다. `loadHallOrders`, `loadKitchenOrders`도 `force`를 실질적으로 쓰지 않는다. 따라서 “baseline에서 force를 넘기면 무조건 최신 응답을 적용한다”고 해석하면 안 된다. 현재 구조는 watermark 비교와 이후 reconcile로 보정하는 쪽에 가깝다.

9. outbound queue overflow에서는 서버가 slow consumer 연결을 끊는다. 이때 `RELOAD_REQUIRED`가 큐에 들어가더라도 emitter가 곧 종료되므로 브라우저가 그 이벤트를 실제로 받는다고 보장할 수 없다. 이 경우 복구는 SSE error/reconnect와 baseline reload 경로가 담당한다.

## 새 POS 기능을 붙일 때 체크리스트

- 도메인 상태 변경이 REST command에서 먼저 확정되는가?
- 변경 후 POS 화면에 필요한 domain event가 발행되는가?
- `PosNotificationPublisher`에 event type과 payload 저장 로직이 있는가?
- `SseNotificationService.knownEventNames`에 event name이 등록되어 있는가?
- `PosStreamChangeService`가 해당 이벤트에서 변경된 entity id를 수집하는가?
- 프론트 `SSE_EVENT_NAMES`와 `eventAdapter`에 매핑이 추가되었는가?
- listener middleware가 즉시 patch할지, snapshot invalidate/reload할지 결정하는가?
- snapshot API가 `X-Pos-Stream-Watermark`를 내려주는가?
- 이벤트 중복 수신/replay 시 같은 변경이 중복 적용되지 않는가?
- replay gap, parse error, unknown event type에서 full reload로 복구되는가?
