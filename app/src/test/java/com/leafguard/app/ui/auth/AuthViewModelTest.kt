package com.leafguard.app.ui.auth

import com.google.common.truth.Truth.assertThat
import com.leafguard.app.domain.model.AuthFailure
import com.leafguard.app.domain.model.AuthOutcome
import com.leafguard.app.domain.model.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun register_mismatchedConfirm_setsPinMismatchFailure_withoutCallingRepo() = runTest {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.register("Ana", "123456", "654321")
        assertThat(vm.failureValue).isEqualTo(AuthFailure.PIN_MISMATCH)
        assertThat(repo.registerCalls).isEqualTo(0)
    }

    @Test
    fun register_success_clearsFailureAndUnlocks() = runTest {
        val repo = FakeAuthRepository(initial = AuthState.Unregistered)
        val vm = AuthViewModel(repo)
        vm.register("Ana", "123456", "123456")
        assertThat(vm.failureValue).isNull()
        assertThat(repo.registerCalls).isEqualTo(1)
        assertThat(repo.lastRegisterArgs).isEqualTo("Ana" to "123456")
        assertThat(vm.authState.value).isInstanceOf(AuthState.Unlocked::class.java)
    }

    @Test
    fun register_invalidPin_bubblesRepoFailure() = runTest {
        val repo = FakeAuthRepository().apply {
            registerOutcome = AuthOutcome.Failure(AuthFailure.INVALID_PIN_FORMAT)
        }
        val vm = AuthViewModel(repo)
        vm.register("Ana", "123456", "123456")
        assertThat(vm.failureValue).isEqualTo(AuthFailure.INVALID_PIN_FORMAT)
    }

    @Test
    fun login_wrongPin_bubblesWrongPinFailure() = runTest {
        val repo = FakeAuthRepository(initial = AuthState.Locked("Ana")).apply {
            loginOutcome = AuthOutcome.Failure(AuthFailure.WRONG_PIN)
        }
        val vm = AuthViewModel(repo)
        vm.login("000000")
        assertThat(vm.failureValue).isEqualTo(AuthFailure.WRONG_PIN)
        assertThat(repo.lastLoginPin).isEqualTo("000000")
    }

    @Test
    fun login_success_unlocks() = runTest {
        val repo = FakeAuthRepository(initial = AuthState.Locked("Ana"))
        val vm = AuthViewModel(repo)
        vm.login("123456")
        assertThat(vm.failureValue).isNull()
        assertThat(vm.authState.value).isInstanceOf(AuthState.Unlocked::class.java)
    }

    @Test
    fun reset_returnsToUnregistered() = runTest {
        val repo = FakeAuthRepository(initial = AuthState.Locked("Ana"))
        val vm = AuthViewModel(repo)
        vm.reset()
        assertThat(vm.authState.value).isEqualTo(AuthState.Unregistered)
    }

    @Test
    fun clearFailure_clearsError() = runTest {
        val repo = FakeAuthRepository(initial = AuthState.Locked("Ana")).apply {
            loginOutcome = AuthOutcome.Failure(AuthFailure.WRONG_PIN)
        }
        val vm = AuthViewModel(repo)
        vm.login("000000")
        assertThat(vm.failureValue).isEqualTo(AuthFailure.WRONG_PIN)
        vm.clearFailure()
        assertThat(vm.failureValue).isNull()
    }
}
