package com.ournight.api_server.api.customer.dto

import com.ournight.api_server.domain.guestbook.model.GuestbookEntry
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

data class CustomerGuestbookCreateRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val content: String,
    @field:Size(max = 3)
    val tags: List<String> = emptyList(),
)

data class CustomerGuestbookEntryResponse(
    val id: UUID,
    val tags: List<String>,
    val content: String,
    val createdAt: LocalDateTime,
)

fun GuestbookEntry.toCustomerResponse(): CustomerGuestbookEntryResponse {
    return CustomerGuestbookEntryResponse(
        id = id ?: error("guestbook id is null"),
        tags = tags.toList(),
        content = content,
        createdAt = createdAt ?: error("guestbook createdAt is null"),
    )
}
