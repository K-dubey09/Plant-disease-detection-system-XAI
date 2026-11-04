package com.leafguard.app.domain.repository

import com.leafguard.app.domain.model.AuthOutcome
import com.leafguard.app.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the user's authentication state. Implemented by
 * [com.leafguard.app.data.repository.AuthRepositoryImpl] and bound by Hilt.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun register(name: String, pin: String): AuthOutcome
    suspend fun login(pin: String): AuthOutcome
    fun logout()
    fun reset()
}
