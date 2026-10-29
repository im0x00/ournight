package com.ournight.api_server.api.host.mapper

import com.ournight.api_server.api.host.dto.*
import com.ournight.api_server.domain.store.view.*

object HostStoreMapper {

    fun toCommand(dto: StoreOnboardingCreateRequest): StoreOnboardingCreateCommand =
        StoreOnboardingCreateCommand(
            type = dto.type,
            name = dto.name,
            description = dto.description,
            hostContact = dto.hostContact,
            timeSlots = dto.timeSlots.map {
                StoreTimeSlotCommand(slotId = it.slotId, startTime = it.startTime, endTime = it.endTime)
            },
            mainImageUrls = dto.mainImageUrls,
        )

    fun toCommand(dto: StoreBasicUpdateRequest): StoreBasicUpdateCommand =
        StoreBasicUpdateCommand(
            name = dto.name,
            description = dto.description,
            mainImageUrls = dto.mainImageUrls,
            venueId = dto.venueId,
            timeSlots = dto.timeSlots?.map {
                StoreTimeSlotCommand(slotId = it.slotId, startTime = it.startTime, endTime = it.endTime)
            },
        )

    fun toCommand(dto: StoreOpsUpdateRequest): StoreOpsUpdateCommand =
        StoreOpsUpdateCommand(
            bankName = dto.bankName,
            accountNumber = dto.accountNumber,
            entranceFee = dto.entranceFee,
            timeLimit = dto.timeLimit,
            notice = dto.notice,
        )

    fun toCommand(dto: StoreOperatingUpdateRequest): StoreOperatingUpdateCommand =
        StoreOperatingUpdateCommand(
            venueId = dto.venueId,
            temporaryVenue = dto.temporaryVenue,
            notice = dto.notice,
            bankName = dto.bankName,
            accountNumber = dto.accountNumber,
            entranceFee = dto.entranceFee,
            timeLimit = dto.timeLimit,
        )

    fun toResponse(result: StoreOnboardingCreateResult): StoreOnboardingCreateResponse =
        StoreOnboardingCreateResponse(storeId = result.storeId)

    fun toResponse(result: StoreDetailResult): StoreDetailResponse =
        StoreDetailResponse(
            storeId = result.storeId,
            type = result.type,
            name = result.name,
            description = result.description,
            hostContact = result.hostContact,
            mainImageUrls = result.mainImageUrls,
            venueId = result.venueId,
            venue = result.venue?.let {
                VenueInfo(
                    venueId = it.venueId,
                    name = it.name,
                    address = it.address,
                    detailLocation = it.detailLocation,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    university = it.university,
                )
            },
            timeSlots = result.timeSlots.map {
                StoreTimeSlotResponse(
                    slotId = it.slotId,
                    startTime = it.startTime,
                    endTime = it.endTime
                )
            },
            bankName = result.bankName,
            accountNumber = result.accountNumber,
            entranceFee = result.entranceFee,
            timeLimit = result.timeLimit,
            notices = result.notices,
        )
}
