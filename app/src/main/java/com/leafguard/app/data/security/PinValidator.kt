package com.leafguard.app.data.security

/**
 * Pure validation rules for the registration PIN. Kept framework-free so the rules and their
 * unit tests are identical on JVM and device.
 */
object PinValidator {
    const val PIN_LENGTH = 6

    fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    fun isBlankName(name: String): Boolean = name.isBlank()
}
