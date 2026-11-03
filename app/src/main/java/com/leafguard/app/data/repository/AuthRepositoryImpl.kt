package com.leafguard.app.data.repository

import com.leafguard.app.data.security.PinStore
import com.leafguard.app.data.security.PinValidator
import com.leafguard.app.domain.model.AuthFailure
import com.leafguard.app.domain.model.AuthOutcome
import com.leafguard.app.domain.model.AuthState
import com.leafguard.app.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [AuthRepository] implementation. Reads the initial state synchronously from [PinStore] so the
 * navigation graph can pick the correct start destination on first composition; all mutating
 * operations run on [ioDispatcher] because EncryptedSharedPreferences does disk I/O.
 */
class AuthRepositoryImpl(
    private val pinStore: PinStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthRepository {

    private val _state = MutableStateFlow(initialState())
    override val authState: StateFlow<AuthState> = _state.asStateFlow()

    private fun initialState(): AuthState =
        if (!pinStore.hasAccount()) {
            AuthState.Unregistered
        } else {
            AuthState.Locked(pinStore.displayName().orEmpty())
        }

    override suspend fun register(name: String, pin: String): AuthOutcome = withContext(ioDispatcher) {
        when {
            PinValidator.isBlankName(name) -> AuthOutcome.Failure(AuthFailure.NAME_BLANK)
            !PinValidator.isValidPin(pin) -> AuthOutcome.Failure(AuthFailure.INVALID_PIN_FORMAT)
            pinStore.hasAccount() -> AuthOutcome.Failure(AuthFailure.ALREADY_REGISTERED)
            !pinStore.register(name, pin) -> AuthOutcome.Failure(AuthFailure.ALREADY_REGISTERED)
            else -> {
                _state.value = AuthState.Unlocked(name.trim())
                AuthOutcome.Success
            }
        }
    }

    override suspend fun login(pin: String): AuthOutcome = withContext(ioDispatcher) {
        when {
            !PinValidator.isValidPin(pin) -> AuthOutcome.Failure(AuthFailure.INVALID_PIN_FORMAT)
            !pinStore.hasAccount() -> AuthOutcome.Failure(AuthFailure.NOT_REGISTERED)
            !pinStore.verify(pin) -> AuthOutcome.Failure(AuthFailure.WRONG_PIN)
            else -> {
                _state.value = AuthState.Unlocked(pinStore.displayName().orEmpty())
                AuthOutcome.Success
            }
        }
    }

    override fun logout() {
        if (pinStore.hasAccount()) {
            _state.value = AuthState.Locked(pinStore.displayName().orEmpty())
        } else {
            _state.value = AuthState.Unregistered
        }
    }

    override fun reset() {
        pinStore.reset()
        _state.value = AuthState.Unregistered
    }
}
