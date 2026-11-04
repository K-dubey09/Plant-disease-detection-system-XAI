package com.leafguard.app.data.inference

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.junit.Test

/**
 * Fix 3: the model-integrity guard. [ModelIntegrity] wraps the same [ByteBuffer] the TFLite
 * Interpreter executes and SHA-256s it against a build-time digest, so a swapped or corrupt
 * .tflite (which TFLite itself would happily run) is detectable and the UI can flag the diagnosis
 * as untrusted. These tests pin the digest math against a known input ("hello") and verify the
 * intact/tampered verdict + the case-insensitive compare + read stability (the duplicate-based read
 * must not corrupt re-reads, since the singleton buffer is reused).
 */
class ModelIntegrityTest {

    // echo -n "hello" | sha256sum
    private val helloSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    @Test
    fun sha256_matchesTheKnownDigestOfHello() {
        val integrity = ModelIntegrity(ByteBuffer.wrap("hello".toByteArray()))
        assertThat(integrity.sha256()).isEqualTo(helloSha256)
    }

    @Test
    fun sha256_is64LowercaseHexChars() {
        val integrity = ModelIntegrity(ByteBuffer.wrap("leafguard".toByteArray()))
        val digest = integrity.sha256()
        assertThat(digest.length).isEqualTo(64)
        assertThat(digest).matches("[0-9a-f]{64}")
    }

    @Test
    fun isIntact_trueWhenTheDigestMatchesTheExpectedValue() {
        val integrity = ModelIntegrity(ByteBuffer.wrap("hello".toByteArray()), expectedSha256 = helloSha256)
        assertThat(integrity.isIntact()).isTrue()
    }

    @Test
    fun isIntact_falseWhenTheModelBufferWasTampered() {
        // "hello" vs "Hello" differ by one bit; the digest changes -> integrity must fail.
        val integrity = ModelIntegrity(ByteBuffer.wrap("Hello".toByteArray()), expectedSha256 = helloSha256)
        assertThat(integrity.isIntact()).isFalse()
    }

    @Test
    fun isIntact_compareIsCaseInsensitiveSoUppercaseHexDigestsStillMatch() {
        val integrity = ModelIntegrity(ByteBuffer.wrap("hello".toByteArray()), expectedSha256 = helloSha256.uppercase())
        assertThat(integrity.isIntact()).isTrue()
    }

    @Test
    fun sha256_isStableAcrossRepeatedReadsAndDoesNotCorruptTheBuffer() {
        // The production ModelIntegrity reuses the singleton model buffer for every check, so the
        // duplicate-based read must leave it usable: two reads must yield the same digest.
        val integrity = ModelIntegrity(ByteBuffer.wrap("hello".toByteArray()))
        val first = integrity.sha256()
        val second = integrity.sha256()
        assertThat(first).isEqualTo(second)
        assertThat(first).isEqualTo(helloSha256)
    }
}
