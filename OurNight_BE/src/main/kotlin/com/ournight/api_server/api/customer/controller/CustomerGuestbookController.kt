package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerGuestbookCreateRequest
import com.ournight.api_server.api.customer.dto.CustomerGuestbookEntryResponse
import com.ournight.api_server.api.customer.dto.toCustomerResponse
import com.ournight.api_server.domain.guestbook.service.GuestbookService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/customer/stores/{storeId}/guestbook")
class CustomerGuestbookController(
    private val guestbookService: GuestbookService,
) {
    @GetMapping
    fun getGuestbookEntries(
        @PathVariable storeId: UUID,
    ): List<CustomerGuestbookEntryResponse> {
        return guestbookService.getEntries(storeId).map { it.toCustomerResponse() }
    }

    @PostMapping
    fun createGuestbookEntry(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
        @RequestBody @Valid request: CustomerGuestbookCreateRequest,
    ): CustomerGuestbookEntryResponse {
        return guestbookService
            .createEntry(
                storeId = storeId,
                user = user,
                content = request.content,
                tags = request.tags,
            )
            .toCustomerResponse()
    }
}
