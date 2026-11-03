package com.leafguard.app.data.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [EncryptedPinStore]: exercises the real Keystore-backed
 * EncryptedSharedPreferences on a device/emulator. Cannot run on the JVM (Robolectric has no
 * real Android Keystore), so it lives under androidTest.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedPinStoreTest {

    private lateinit var store: EncryptedPinStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = EncryptedPinStore(context, PinHasher())
        store.reset() // start clean
    }

    @After
    fun tearDown() {
        store.reset()
    }

    @Test
    fun emptyStore_hasNoAccount() {
        assertThat(store.hasAccount()).isFalse()
        assertThat(store.displayName()).isNull()
    }

    @Test
    fun register_createsAccountAndStoresName() {
        val ok = store.register("Ana", "123456")
        assertThat(ok).isTrue()
        assertThat(store.hasAccount()).isTrue()
        assertThat(store.displayName()).isEqualTo("Ana")
    }

    @Test
    fun register_twice_secondFails() {
        store.register("Ana", "123456")
        assertThat(store.register("Bo", "654321")).isFalse()
    }

    @Test
    fun verify_acceptsCorrectPin() {
        store.register("Ana", "123456")
        assertThat(store.verify("123456")).isTrue()
    }

    @Test
    fun verify_rejectsWrongPin() {
        store.register("Ana", "123456")
        assertThat(store.verify("000000")).isFalse()
    }

    @Test
    fun reset_clearsAccount() {
        store.register("Ana", "123456")
        store.reset()
        assertThat(store.hasAccount()).isFalse()
        assertThat(store.displayName()).isNull()
        assertThat(store.verify("123456")).isFalse()
    }

    @Test
    fun pinSurvivesAcrossInstances() {
        // A new store object over the same encrypted file must still verify the PIN.
        store.register("Ana", "123456")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reopened = EncryptedPinStore(context, PinHasher())
        assertThat(reopened.hasAccount()).isTrue()
        assertThat(reopened.displayName()).isEqualTo("Ana")
        assertThat(reopened.verify("123456")).isTrue()
    }
}
