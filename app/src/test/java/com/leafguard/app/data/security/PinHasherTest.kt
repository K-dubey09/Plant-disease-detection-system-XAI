package com.leafguard.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.SecureRandom

/**
 * JVM unit tests for [PinHasher]. Uses a low iteration count to keep the suite fast while still
 * exercising the real PBKDF2-HMAC-SHA256 path of [javax.crypto.SecretKeyFactory].
 */
class PinHasherTest {

    // Low iterations for test speed; production uses the default 100,000.
    private val hasher = PinHasher(iterations = 1_000)

    @Test
    fun hash_isDeterministic_forSamePinAndSalt() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val a = hasher.hash("123456", salt)
        val b = hasher.hash("123456", salt)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun hash_differsForDifferentPins() {
        val salt = hasher.newSalt()
        assertThat(hasher.hash("123456", salt)).isNotEqualTo(hasher.hash("654321", salt))
    }

    @Test
    fun hash_differsForDifferentSalts() {
        val s1 = hasher.newSalt()
        val s2 = hasher.newSalt()
        assertThat(s1).isNotEqualTo(s2)
        assertThat(hasher.hash("123456", s1)).isNotEqualTo(hasher.hash("123456", s2))
    }

    @Test
    fun verify_acceptsCorrectPin() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("000000", salt)
        assertThat(hasher.verify("000000", salt, hash)).isTrue()
    }

    @Test
    fun verify_rejectsWrongPin() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("111111", salt)
        assertThat(hasher.verify("222222", salt, hash)).isFalse()
    }

    @Test
    fun verify_rejectsEmptyExpectedHash() {
        val salt = hasher.newSalt()
        assertThat(hasher.verify("123456", salt, ByteArray(0))).isFalse()
    }

    @Test
    fun newSalt_isNonEmptyAnd16Bytes() {
        assertThat(hasher.newSalt()).hasLength(PinHasher.SALT_BYTES)
    }

    @Test(expected = IllegalArgumentException::class)
    fun hash_rejectsEmptyPin() {
        hasher.hash("", hasher.newSalt())
    }
}
