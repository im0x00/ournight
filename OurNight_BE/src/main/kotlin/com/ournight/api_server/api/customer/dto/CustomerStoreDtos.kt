package com.ournight.api_server.api.customer.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

//주점 목록 조회용 DTO (가벼운 정보)
data class CustomerStoreSummaryResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val type: String,
    val latitude: Double?,   
    val longitude: Double?   
)

data class CustomerStoreByDateResponse(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
    val type: String,
    val address: String,
    val universityId: UUID?,
    val universityName: String?,
    val isScrapped: Boolean,
    val timeSlots: List<CustomerStoreTimeSlotResponse>,
)

data class CustomerStoreByBoundsResponse(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
    val type: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isScrapped: Boolean,
    val timeSlots: List<CustomerStoreTimeSlotResponse>,
)

data class CustomerStoreDateRangeCountResponse(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val startDate: LocalDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val endDate: LocalDate,
    val counts: Map<LocalDate, Int>,
)

data class CustomerStoreTimeSlotResponse(
    val slotId: UUID,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    val startTime: LocalDateTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    val endTime: LocalDateTime,
    val hasWaiting: Boolean,
)

//주점 상세 조회용 DTO (상세 정보)
data class CustomerStoreDetailResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val type: String,
    val address: String?,       // 상세 주소 (venue가 없을 수 있음)
    val bankName: String?,      // 계좌 정보 
    val accountNumber: String?, // 계좌 번호
    val entranceFee: BigDecimal?, // 입장료(원), 미설정 시 null
    val notices: List<String>,
    val imageUrls: List<String>, // 가게 대표/추가 사진 URL 목록
    val timeSlots: List<CustomerStoreTimeSlotResponse>,
)
