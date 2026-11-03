package com.leafguard.app.data.security

/**
 * In-memory [PinStore] for JVM unit tests. Uses a real [PinHasher] (low iterations) so verify()
 * is exercised end-to-end without touching the Android Keystore or EncryptedSharedPreferences.
 */
class FakePinStore(
    hasher: PinHasher = PinHasher(iterations = 1_000),
) : PinStore {

    private val hasher = hasher
    private var name: String? = null
    private var salt: ByteArray? = null
    private var hash: ByteArray? = null
    var resetCount = 0
        private set

    override fun hasAccount(): Boolean = hash != null

    override fun displayName(): String? = name

    override fun register(name: String, pin: String): Boolean {
        if (hash != null) return false
        val s = hasher.newSalt()
        this.name = name.trim()
        this.salt = s
        this.hash = hasher.hash(pin, s)
        return true
    }

    override fun verify(pin: String): Boolean {
        val s = salt ?: return false
        val h = hash ?: return false
        return hasher.verify(pin, s, h)
    }

    override fun reset() {
        name = null
        salt = null
        hash = null
        resetCount += 1
    }
}
