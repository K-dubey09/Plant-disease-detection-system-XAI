package com.leafguard.app.data.security

/**
 * Persistence contract for the local account credential. The production implementation
 * [EncryptedPinStore] writes through Keystore-backed EncryptedSharedPreferences; a fake
 * in-memory implementation is used in JVM unit tests of the repository.
 */
interface PinStore {
    /** True once a user has registered (display name + PIN hash present). */
    fun hasAccount(): Boolean

    /** The registered display name, or null if no account exists. */
    fun displayName(): String?

    /**
     * Create the account. Returns false if an account already exists (idempotency guard).
     * [pin] is hashed and discarded; only the salt + hash are stored.
     */
    fun register(name: String, pin: String): Boolean

    /** Verify [pin] against the stored hash. False if no account or wrong PIN. */
    fun verify(pin: String): Boolean

    /** Wipe the account so the app returns to the registration flow. */
    fun reset()
}
