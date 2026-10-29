package com.ournight.api_server.domain.auth.repository

import com.ournight.api_server.domain.auth.model.RefreshToken
import org.springframework.data.repository.CrudRepository

interface RefreshTokenRepository: CrudRepository<RefreshToken, String>