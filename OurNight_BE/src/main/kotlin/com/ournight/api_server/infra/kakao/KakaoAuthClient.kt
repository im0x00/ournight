package com.ournight.api_server.infra.kakao

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "KakaoApiClient", url = "https://kauth.kakao.com")
interface KakaoAuthClient {
    @PostMapping("/oauth/token")
    fun getAccessToken(
        @RequestParam("grant_type") grantType: String = "authorization_code",
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("code") code: String,
        @RequestParam("client_secret") clientSecret: String,
    ): KakaoDtos.KakaoTokenResponse
}