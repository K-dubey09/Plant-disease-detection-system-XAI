package com.leafguard.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinValidatorTest {

    @Test
    fun validPin_isExactlySixDigits() {
        assertThat(PinValidator.isValidPin("123456")).isTrue()
        assertThat(PinValidator.isValidPin("000000")).isTrue()
        assertThat(PinValidator.isValidPin("999999")).isTrue()
    }

    @Test
    fun tooShort_isInvalid() {
        assertThat(PinValidator.isValidPin("12345")).isFalse()
    }

    @Test
    fun tooLong_isInvalid() {
        assertThat(PinValidator.isValidPin("1234567")).isFalse()
    }

    @Test
    fun nonDigits_areInvalid() {
        assertThat(PinValidator.isValidPin("12a456")).isFalse()
        assertThat(PinValidator.isValidPin("12 456")).isFalse()
        assertThat(PinValidator.isValidPin("12.456")).isFalse()
    }

    @Test
    fun empty_isInvalid() {
        assertThat(PinValidator.isValidPin("")).isFalse()
    }

    @Test
    fun blankName_detected() {
        assertThat(PinValidator.isBlankName("")).isTrue()
        assertThat(PinValidator.isBlankName("   ")).isTrue()
        assertThat(PinValidator.isBlankName("Ana")).isFalse()
    }
}
