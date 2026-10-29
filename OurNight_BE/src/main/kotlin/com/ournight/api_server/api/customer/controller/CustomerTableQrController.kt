package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerTableQrResponse
import com.ournight.api_server.api.customer.dto.toCustomerTableQrResponse
import com.ournight.api_server.domain.pass.service.PassService
import com.ournight.api_server.domain.store.application.StoreTableService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/customer/stores/{storeId}/table-qrs")
class CustomerTableQrController(
    private val passService: PassService,
    private val storeTableService: StoreTableService,
) {
    @GetMapping
    fun getTableQrs(
        @LoginUser user: User,
        @PathVariable storeId: UUID,
    ): List<CustomerTableQrResponse> {
        passService.ensureOrderablePassForStore(user.id!!, storeId)
        return storeTableService.getStoreTables(storeId).map { it.toCustomerTableQrResponse() }
    }
}
