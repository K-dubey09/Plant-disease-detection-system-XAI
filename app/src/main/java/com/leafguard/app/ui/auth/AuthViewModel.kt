package com.leafguard.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leafguard.app.data.security.PinValidator
import com.leafguard.app.domain.model.AuthFailure
import com.leafguard.app.domain.model.AuthState
import com.leafguard.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the register and login screens. Exposes the global [authState] (observed by the
 * navigation graph for gating) and a [failure] one-shot the screen maps to a localized error.
 *
 * Stays free of Android resource references so it is JVM-unit-testable with a fake repository.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.authState.value)

    private val _failure = MutableStateFlow<AuthFailure?>(null)
    val failure: StateFlow<AuthFailure?> = _failure.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun register(name: String, pin: String, confirm: String) {
        if (_busy.value) return
        // UI-level rule: confirm must match. Validated here (not the repository) so the
        // repository stays single-purpose and the test surface is small.
        if (pin != confirm) {
            _failure.value = AuthFailure.PIN_MISMATCH
            return
        }
        submit {
            authRepository.register(name, pin)
        }
    }

    fun login(pin: String) {
        if (_busy.value) return
        submit {
            authRepository.login(pin)
        }
    }

    fun reset() {
        if (_busy.value) return
        authRepository.reset()
        _failure.value = null
    }

    fun clearFailure() {
        _failure.value = null
    }

    private fun submit(action: suspend () -> com.leafguard.app.domain.model.AuthOutcome) {
        viewModelScope.launch {
            _busy.value = true
            _failure.value = null
            val outcome = try {
                action()
            } finally {
                _busy.value = false
            }
            if (outcome is com.leafguard.app.domain.model.AuthOutcome.Failure) {
                _failure.value = outcome.reason
            }
        }
    }

    /** Test affordance: construct with a specific state for deterministic VM tests. */
    object Flags {
        const val PIN_LENGTH = PinValidator.PIN_LENGTH
    }
}
