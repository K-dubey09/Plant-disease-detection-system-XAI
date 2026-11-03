package com.leafguard.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Production [PinStore] backed by [EncryptedSharedPreferences].
 *
 * The preferences file is encrypted at rest with an AES-256-GCM master key bound to the
 * Android Keystore, so the credential material is unreadable without the device's Keystore
 * even if the file is copied off. On top of that at-rest encryption, the PIN itself is never
 * stored: only a random salt and the PBKDF2 derived key from [PinHasher] are persisted.
 *
 * This class touches Android framework crypto and is therefore exercised by instrumented
 * tests on a device, not by JVM unit tests.
 */
class EncryptedPinStore(
    private val context: Context,
    private val hasher: PinHasher,
) : PinStore {

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun hasAccount(): Boolean = prefs.contains(KEY_PIN_HASH)

    override fun displayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)

    override fun register(name: String, pin: String): Boolean {
        if (hasAccount()) return false
        val salt = hasher.newSalt()
        val hash = hasher.hash(pin, salt)
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, name.trim())
            .putString(KEY_PIN_SALT, encode(salt))
            .putString(KEY_PIN_HASH, encode(hash))
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    override fun verify(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = decode(saltB64)
        val expected = decode(hashB64)
        return hasher.verify(pin, salt, expected)
    }

    override fun reset() {
        prefs.edit().clear().apply()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(b64: String): ByteArray =
        Base64.decode(b64, Base64.NO_WRAP)

    companion object {
        private const val FILE_NAME = "leafguard_secure.xml"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_CREATED_AT = "created_at"

        /**
         * Marker exposed for backup-rule exclusion (the file must never be backed up). Kept here
         * so the resource reference and the runtime filename stay in sync.
         */
        const val BACKUP_EXCLUDED_PATH = FILE_NAME

        @Suppress("unused")
        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
