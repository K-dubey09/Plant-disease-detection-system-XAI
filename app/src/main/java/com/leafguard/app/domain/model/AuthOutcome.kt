package com.leafguard.app.domain.model

/**
 * Result of an authentication attempt. The UI maps [AuthFailure] to a localized string so the
 * ViewModel can stay free of Android resource references and remain JVM-unit-testable.
 */
sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Failure(val reason: AuthFailure) : AuthOutcome
}

enum class AuthFailure {
    NAME_BLANK,
    INVALID_PIN_FORMAT,
    PIN_MISMATCH,
    ALREADY_REGISTERED,
    WRONG_PIN,
    NOT_REGISTERED,
}
