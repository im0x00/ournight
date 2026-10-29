package com.ournight.api_server.domain.call.model

import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.domain.session.model.TableSession
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class CallStatus {
    PENDING, // 호출 됨 (알림 울림)
    COMPLETED // 처리 완료
}


// 1. Host가 정의하는 호출 옵션 (메타데이터)
@Entity
class CallOption(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    val store: Store,

    var name: String, // 예: "물 주세요", "직원 호출", "앞치마"

    var isActive: Boolean = true // Host가 잠시 숨길 수 있도록
) {

    companion object {
        private val ONBOARDING_DEFAULT_NAMES = listOf(
            "물 주세요",
            "티슈 주세요",
            "수저 주세요",
            "컵 주세요",
            "다른 요청사항이 있어요",
        )

        /** 스토어 온보딩 시 자동 생성되는 기본 호출 옵션(전부 활성) */
        fun onboardingDefaultsFor(store: Store): List<CallOption> =
            ONBOARDING_DEFAULT_NAMES.map { name ->
                CallOption(store = store, name = name, isActive = true)
            }
    }

    fun update(name: String, isActive: Boolean) {
        this.name = name
        this.isActive = isActive
    }
}

// 2. Customer가 실제로 보낸 호출 요청
@Entity
class CallRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    val table: StoreTable,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_option_id")
    val callOption: CallOption, // 어떤 옵션을 선택했는지 연결

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id")
    val tableSession: TableSession? = null,

    @Enumerated(EnumType.STRING)
    var status: CallStatus = CallStatus.PENDING, // 대기중, 처리완료 등

    val createdAt: LocalDateTime
) {

    fun complete() {
        if (this.status != CallStatus.PENDING) {
            throw IllegalStateException("대기 중인 호출만 완료할 수 있습니다.")
        }
        this.status = CallStatus.COMPLETED
    }

}
