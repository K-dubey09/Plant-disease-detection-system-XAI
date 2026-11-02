package com.leafguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import com.leafguard.app.R
import com.leafguard.app.ui.components.LeafScaffold

/**
 * PIN-based authentication screen skeleton.
 *
 * Two modes share one composable: REGISTER (first launch, name + 6-digit PIN + confirm) and
 * LOGIN (returning user, 6-digit PIN). The UI is fully built and interactive here so the
 * navigation flow can be demonstrated; the actual credential persistence (EncryptedSharedPreferences
 * keyed to the Android Keystore, biometric fallback) and the `onAuthenticated` gating land in
 * Phase 2. Pressing the primary button calls [onAuthenticated] unconditionally for now.
 */
@Composable
fun AuthScreen(
    mode: AuthScreen.Mode,
    onAuthenticated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

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
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.auth_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = pin,
                onValueChange = { value -> if (value.all { it.isDigit() } && value.length <= 6) pin = value },
                label = { Text(stringResource(R.string.auth_pin)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == AuthScreen.Mode.REGISTER) {
                OutlinedTextField(
                    value = pinConfirm,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 6) pinConfirm = value
                    },
                    label = { Text(stringResource(R.string.auth_pin_confirm)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = onAuthenticated,
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

            TextButton(onClick = onAuthenticated) {
                Text(
                    text = if (mode == AuthScreen.Mode.REGISTER) {
                        "Already registered? Unlock instead"
                    } else {
                        "Reset LeafGuard (re-register)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.placeholder_phase, "Authentication", 2),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

object AuthScreen {
    enum class Mode { REGISTER, LOGIN }
}
