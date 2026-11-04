package com.leafguard.app.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.leafguard.app.data.security.FakePinStore
import com.leafguard.app.domain.model.AuthFailure
import com.leafguard.app.domain.model.AuthOutcome
import com.leafguard.app.domain.model.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    private fun repo(store: FakePinStore = FakePinStore()) =
        AuthRepositoryImpl(store, ioDispatcher = UnconfinedTestDispatcher())

    @Test
    fun initialState_isUnregistered_whenNoAccount() = runTest {
        assertThat(repo().authState.value).isEqualTo(AuthState.Unregistered)
    }

    @Test
    fun initialState_isLocked_whenAccountExists() = runTest {
        val store = FakePinStore().apply { register("Ana", "123456") }
        val state = repo(store).authState.value
        assertThat(state).isInstanceOf(AuthState.Locked::class.java)
        assertThat((state as AuthState.Locked).displayName).isEqualTo("Ana")
    }

    @Test
    fun register_success_unlocksAndEmits() = runTest {
        val r = repo()
        r.authState.test {
            assertThat(awaitItem()).isEqualTo(AuthState.Unregistered)
            val outcome = r.register("Ana", "123456")
            assertThat(outcome).isEqualTo(AuthOutcome.Success)
            val unlocked = awaitItem()
            assertThat(unlocked).isInstanceOf(AuthState.Unlocked::class.java)
            assertThat((unlocked as AuthState.Unlocked).displayName).isEqualTo("Ana")
        }
    }

    @Test
    fun register_blankName_fails() = runTest {
        val r = repo()
        assertThat(r.register("   ", "123456")).isEqualTo(AuthOutcome.Failure(AuthFailure.NAME_BLANK))
        assertThat(r.authState.value).isEqualTo(AuthState.Unregistered)
    }

    @Test
    fun register_invalidPin_fails() = runTest {
        val r = repo()
        assertThat(r.register("Ana", "12345")).isEqualTo(AuthOutcome.Failure(AuthFailure.INVALID_PIN_FORMAT))
        assertThat(r.register("Ana", "12a456")).isEqualTo(AuthOutcome.Failure(AuthFailure.INVALID_PIN_FORMAT))
    }

    @Test
    fun register_whenAlreadyRegistered_fails() = runTest {
        val store = FakePinStore().apply { register("Ana", "123456") }
        val r = repo(store)
        assertThat(r.register("Bo", "654321")).isEqualTo(AuthOutcome.Failure(AuthFailure.ALREADY_REGISTERED))
    }

    @Test
    fun login_wrongPin_fails() = runTest {
        val store = FakePinStore().apply { register("Ana", "123456") }
        val r = repo(store)
        assertThat(r.login("000000")).isEqualTo(AuthOutcome.Failure(AuthFailure.WRONG_PIN))
        assertThat(r.authState.value).isInstanceOf(AuthState.Locked::class.java)
    }

    @Test
    fun login_notRegistered_fails() = runTest {
        val r = repo()
        assertThat(r.login("123456")).isEqualTo(AuthOutcome.Failure(AuthFailure.NOT_REGISTERED))
    }

    @Test
    fun login_success_unlocks() = runTest {
        val store = FakePinStore().apply { register("Ana", "123456") }
        val r = repo(store)
        assertThat(r.login("123456")).isEqualTo(AuthOutcome.Success)
        val state = r.authState.value
        assertThat(state).isInstanceOf(AuthState.Unlocked::class.java)
        assertThat((state as AuthState.Unlocked).displayName).isEqualTo("Ana")
    }

    @Test
    fun logout_returnsToLocked() = runTest {
        val store = FakePinStore().apply { register("Ana", "123456") }
        val r = repo(store)
        r.login("123456")
        r.logout()
        assertThat(r.authState.value).isInstanceOf(AuthState.Locked::class.java)
    }

    @Test
    fun reset_clearsAccountAndReturnsToUnregistered() = runTest {
        val store = FakePinStore().apply { register("Ana", "123456") }
        val r = repo(store)
        r.reset()
        assertThat(store.resetCount).isEqualTo(1)
        assertThat(r.authState.value).isEqualTo(AuthState.Unregistered)
    }
}
