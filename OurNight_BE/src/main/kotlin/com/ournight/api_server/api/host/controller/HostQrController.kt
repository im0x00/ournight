package com.ournight.api_server.api.host.controller

import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import com.ournight.api_server.infra.qr.QrCodeService
import com.ournight.api_server.infra.qr.QrGenerateResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/host/qr")
class HostQrController(
    private val qrCodeService: QrCodeService
) {

    @PostMapping("/generate")
    fun generateQrCodes(
        @LoginUser user: User,
        @RequestBody request: QrGenerateRequest
    ): ResponseEntity<List<QrGenerateResult>> {
        require(request.qrLinks.isNotEmpty()) { "링크 목록이 비어있습니다." }

        val result = qrCodeService.getOrGenerateQrImages(request.qrLinks)
        return ResponseEntity.ok(result)
    }
}

data class QrGenerateRequest(
    val qrLinks: List<String>
)
