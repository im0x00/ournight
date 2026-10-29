package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerStoreByDateResponse
import com.ournight.api_server.domain.store.application.StoreScrapService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/customer/stores")
class CustomerStoreScrapController(
    private val storeScrapService: StoreScrapService,
) {
    @PostMapping("/{storeId}/scrap")
    fun scrapStore(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): ResponseEntity<Void> {
        storeScrapService.scrapStore(user.id!!, storeId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{storeId}/scrap")
    fun unscrapStore(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): ResponseEntity<Void> {
        storeScrapService.unscrapStore(user.id!!, storeId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/scraps")
    fun getScrappedStores(
        @LoginUser user: User,
    ): ResponseEntity<List<CustomerStoreByDateResponse>> {
        val response = storeScrapService.getMyScrappedStores(user.id!!)
        return ResponseEntity.ok(response)
    }
}
