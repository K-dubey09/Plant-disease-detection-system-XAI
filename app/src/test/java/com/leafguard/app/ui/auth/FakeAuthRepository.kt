package com.leafguard.app.ui.auth

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.AuthFailure
import com.leafguard.app.domain.model.AuthOutcome
import com.leafguard.app.domain.model.AuthState
import com.leafguard.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [AuthRepository] for [AuthViewModel] JVM tests. register/login return a configured
 * outcome and, on success, advance the state to Unlocked so the VM observes a realistic flow.
 */
class FakeAuthRepository(initial: AuthState = AuthState.Unregistered) : AuthRepository {

    override val authState: StateFlow<AuthState> = MutableStateFlow(initial)
    private val mutableState get() = authState as MutableStateFlow

    var registerCalls = 0
        private set
    var loginCalls = 0
        private set
    var lastRegisterArgs: Pair<String, String>? = null
        private set
    var lastLoginPin: String? = null
        private set

    var registerOutcome: AuthOutcome = AuthOutcome.Success
    var loginOutcome: AuthOutcome = AuthOutcome.Success

    override suspend fun register(name: String, pin: String): AuthOutcome {
        registerCalls += 1
        lastRegisterArgs = name to pin
        if (registerOutcome is AuthOutcome.Success) {
            mutableState.value = AuthState.Unlocked(name.trim())
        }
        return registerOutcome
    }

    override suspend fun login(pin: String): AuthOutcome {
        loginCalls += 1
        lastLoginPin = pin
        if (loginOutcome is AuthOutcome.Success) {
            val name = (authState.value as? AuthState.Locked)?.displayName ?: "User"
            mutableState.value = AuthState.Unlocked(name)
        }
        return loginOutcome
    }

    override fun logout() {
        val name = (authState.value as? AuthState.Unlocked)?.displayName ?: ""
        mutableState.value = AuthState.Locked(name)
    }

    override fun reset() {
        mutableState.value = AuthState.Unregistered
    }
}

internal val AuthViewModel.failureValue: AuthFailure?
    get() = failure.value

internal val AuthViewModel.isBusy: Boolean
    get() = busy.value
