package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.domain.model.AuthFailure
import com.leafguard.app.ui.auth.AuthViewModel
import com.leafguard.app.ui.components.LeafScaffold

/**
 * PIN-based authentication screen. Two modes share one composable: REGISTER (first launch)
 * and LOGIN (returning user). Field state is local to the composable; authentication runs
 * through [AuthViewModel], whose [AuthViewModel.authState] is observed by the navigation
 * graph to move the user into the main app on success.
 */
@Composable
fun AuthScreen(
    mode: AuthScreen.Mode,
    viewModel: AuthViewModel,
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val title = when (mode) {
        AuthScreen.Mode.REGISTER -> stringResource(R.string.auth_register_title)
        AuthScreen.Mode.LOGIN -> stringResource(R.string.auth_login_title)
    }

    LeafScaffold(title = title) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            if (mode == AuthScreen.Mode.REGISTER) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value ->
                        name = value
                        viewModel.clearFailure()
                    },
                    label = { Text(stringResource(R.string.auth_name)) },
                    singleLine = true,
                    isError = failure == AuthFailure.NAME_BLANK,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    if (value.all { it.isDigit() } && value.length <= 6) {
                        pin = value
                        viewModel.clearFailure()
                    }
                },
                label = { Text(stringResource(R.string.auth_pin)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                isError = failure == AuthFailure.INVALID_PIN_FORMAT || failure == AuthFailure.WRONG_PIN,
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == AuthScreen.Mode.REGISTER) {
                OutlinedTextField(
                    value = pinConfirm,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 6) {
                            pinConfirm = value
                            viewModel.clearFailure()
                        }
                    },
                    label = { Text(stringResource(R.string.auth_pin_confirm)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = failure == AuthFailure.PIN_MISMATCH,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            failure?.let { f ->
                Text(
                    text = stringResource(f.toRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = {
                    if (mode == AuthScreen.Mode.REGISTER) {
                        viewModel.register(name, pin, pinConfirm)
                    } else {
                        viewModel.login(pin)
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = stringResource(
                        if (mode == AuthScreen.Mode.REGISTER) R.string.auth_register_button
                        else R.string.auth_login_button
                    ),
                )
            }

            TextButton(onClick = {
                if (mode == AuthScreen.Mode.LOGIN) showResetDialog = true else viewModel.reset()
            }) {
                Text(
                    text = if (mode == AuthScreen.Mode.REGISTER) {
                        "Already registered? Unlock instead"
                    } else {
                        "Reset LeafGuard (re-register)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(stringResource(R.string.auth_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.reset()
                }) { Text(stringResource(R.string.auth_reset_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun AuthFailure.toRes(): Int = when (this) {
    AuthFailure.NAME_BLANK -> R.string.auth_error_name_blank
    AuthFailure.INVALID_PIN_FORMAT -> R.string.auth_error_pin_format
    AuthFailure.PIN_MISMATCH -> R.string.auth_error_pin_mismatch
    AuthFailure.ALREADY_REGISTERED -> R.string.auth_error_already_registered
    AuthFailure.WRONG_PIN -> R.string.auth_error_wrong_pin
    AuthFailure.NOT_REGISTERED -> R.string.auth_error_not_registered
}

object AuthScreen {
    enum class Mode { REGISTER, LOGIN }
}
