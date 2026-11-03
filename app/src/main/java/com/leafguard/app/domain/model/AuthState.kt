package com.leafguard.app.domain.model

/**
 * The authentication state of the app, observed by the navigation graph to gate between the
 * auth flow and the main app.
 *
 * - [Unregistered]: no account exists yet -> show the register screen.
 * - [Locked]: an account exists but the app is not unlocked this session -> show the PIN login.
 * - [Unlocked]: the user authenticated this session -> show the main app.
 */
sealed interface AuthState {
    data object Unregistered : AuthState
    data class Locked(val displayName: String) : AuthState
    data class Unlocked(val displayName: String) : AuthState
}
