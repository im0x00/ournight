package com.ournight.api_server.api.customer.controller

import com.ournight.api_server.api.customer.dto.CustomerMyPassResponse
import com.ournight.api_server.api.customer.dto.toMyPassResponse
import com.ournight.api_server.domain.pass.service.PassService
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customer/passes")
class CustomerMyPassController(
    private val passService: PassService,
) {
    @GetMapping
    fun getMyPasses(
        @LoginUser user: User,
    ): List<CustomerMyPassResponse> =
        passService.getMyPasses(user.id!!).map { it.toMyPassResponse() }
}
