package com.leafguard.app.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 PIN hashing. Pure JDK crypto ([SecretKeyFactory], no Android framework),
 * so it is fully unit-testable on the JVM.
 *
 * The PIN is never stored. Instead we store a random 16-byte [salt] and the derived key bytes
 * from [hash]; verification re-derives and compares with a constant-time [MessageDigest.isEqual].
 * 100,000 iterations make offline brute force of a 6-digit PIN impractical even if the store
 * were exfiltrated (defense in depth on top of EncryptedSharedPreferences at-rest encryption).
 */
class PinHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
) {

    fun newSalt(size: Int = SALT_BYTES): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        require(salt.isNotEmpty()) { "Salt must not be empty" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time verification. */
    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean {
        if (expected.isEmpty()) return false
        val actual = hash(pin, salt)
        return MessageDigest.isEqual(actual, expected)
    }

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 100_000
        const val DEFAULT_KEY_LENGTH_BITS = 256
        const val SALT_BYTES = 16
    }
}
