package com.ournight.api_server.infra.kakao

import com.fasterxml.jackson.annotation.JsonProperty

class KakaoDtos {
    data class KakaoTokenResponse( // 카카오가 주는 토큰 응답
        @JsonProperty("access_token") val accessToken: String
    )

    data class KakaoUserInfoResponse( // 카카오가 주는 유저 정보 응답
        val id: Long,
        val properties: KakaoProperties? = null,
        @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount? = null
    )

    data class KakaoProperties(
        val nickname: String? = null
    )

    data class KakaoAccount(
        val profile: KakaoProfile? = null
    )

    data class KakaoProfile(
        val nickname: String? = null
    )
}
