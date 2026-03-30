package com.example.widget_android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.widget_android.BuildConfig
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.network.ApiClient
import com.example.widget_android.network.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun LoginScreen(
    repository: ClockingApiRepository,
    onLoggedIn: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveBaseUrl = remember { ApiClient.effectiveBaseUrl(context) }

    var email by remember { mutableStateOf("test@demo.com") }
    var password by remember { mutableStateOf("1234") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Fichajes",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Servidor: $effectiveBaseUrl",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (effectiveBaseUrl.contains("127.0.0.1")) {
                Text(
                    text = "Móvil físico: en el PC ejecuta adb reverse tcp:8080 tcp:8080",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Completa email y contraseña"
                    return@Button
                }
                loading = true
                error = null
                scope.launch {
                    try {
                        val result =
                            withTimeout(50_000L) {
                                withContext(Dispatchers.IO) {
                                    repository.login(email, password)
                                }
                            }
                        result.fold(
                            onSuccess = { token -> onLoggedIn(token) },
                            onFailure = { e -> error = e.toUserMessage() }
                        )
                    } catch (_: TimeoutCancellationException) {
                        error =
                            "Tiempo de espera agotado. Comprueba docker compose (backend, puerto 8080) y adb reverse tcp:8080 tcp:8080 en USB."
                    } catch (e: Exception) {
                        error = e.toUserMessage()
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Entrando…" else "Entrar")
        }

        error?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
