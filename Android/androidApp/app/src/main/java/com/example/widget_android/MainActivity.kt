package com.example.widget_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.SessionRepository
import com.example.widget_android.data.TokenHolder
import com.example.widget_android.theme.WidgetandroidTheme
import com.example.widget_android.ui.DashboardScreen
import com.example.widget_android.ui.LoginScreen
import com.example.widget_android.ui.QuickActionDestination
import com.example.widget_android.ui.QuickActionScreen
import com.example.widget_android.widget.FichajeWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var requestedDestination by mutableStateOf<QuickActionDestination?>(null)
    private var localSessionRefreshSignal by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDestination = resolveRequestedDestination(intent)
        localSessionRefreshSignal++
        setContent {
            WidgetandroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        requestedDestination = requestedDestination,
                        localSessionRefreshSignal = localSessionRefreshSignal,
                        onRequestedDestinationConsumed = { consumeRequestedDestination() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination = resolveRequestedDestination(intent)
        localSessionRefreshSignal++
    }

    private fun resolveRequestedDestination(intent: Intent?): QuickActionDestination? =
        QuickActionDestination.fromValue(
            intent?.getStringExtra(QuickActionDestination.EXTRA_QUICK_ACTION_DESTINATION)
        )

    private fun consumeRequestedDestination() {
        requestedDestination = null
        intent?.removeExtra(QuickActionDestination.EXTRA_QUICK_ACTION_DESTINATION)
    }
}

@Composable
private fun AppRoot(
    requestedDestination: QuickActionDestination?,
    localSessionRefreshSignal: Int,
    onRequestedDestinationConsumed: () -> Unit
) {
    val context = LocalContext.current
    val appCtx = remember { context.applicationContext }
    val repository = remember { ClockingApiRepository(appCtx) }
    val session = remember { SessionRepository(appCtx) }
    var token by remember { mutableStateOf<String?>(TokenHolder.token) }
    var bootstrapped by remember { mutableStateOf(false) }
    var pendingDestinationValue by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDestinationValue by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activeDestination = QuickActionDestination.fromValue(activeDestinationValue)

    LaunchedEffect(Unit) {
        val storedToken = withContext(Dispatchers.IO) { session.readToken() }
        if (!storedToken.isNullOrBlank()) {
            TokenHolder.token = storedToken
            val restoreResult = withContext(Dispatchers.IO) { repository.restoreSession() }
            if (restoreResult.isSuccess && restoreResult.getOrNull() == true) {
                val refreshedToken = withContext(Dispatchers.IO) { session.readToken() }
                token = refreshedToken
                TokenHolder.token = refreshedToken
            } else {
                // Si la sesión no es válida (401 o expirada), limpiamos para forzar login
                token = null
                TokenHolder.token = null
                withContext(Dispatchers.IO) { repository.clearLocalSession() }
            }
        } else {
            token = null
            TokenHolder.token = null
        }
        bootstrapped = true
    }

    LaunchedEffect(localSessionRefreshSignal) {
        if (!bootstrapped) return@LaunchedEffect
        val storedToken = withContext(Dispatchers.IO) { session.readToken() }
        token = storedToken
        if (!storedToken.isNullOrBlank()) {
            TokenHolder.token = storedToken
        }
    }

    LaunchedEffect(requestedDestination, bootstrapped, token) {
        val destination = requestedDestination ?: return@LaunchedEffect
        pendingDestinationValue = destination.value
        if (bootstrapped && !token.isNullOrBlank()) {
            activeDestinationValue = destination.value
            pendingDestinationValue = null
        }
        onRequestedDestinationConsumed()
    }

    LaunchedEffect(bootstrapped, token, pendingDestinationValue) {
        if (bootstrapped && !token.isNullOrBlank() && pendingDestinationValue != null) {
            activeDestinationValue = pendingDestinationValue
            pendingDestinationValue = null
        }
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank() && activeDestinationValue != null) {
            pendingDestinationValue = activeDestinationValue
            activeDestinationValue = null
        }
    }

    when {
        !bootstrapped -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        token.isNullOrBlank() -> {
            LoginScreen(
                repository = repository,
                onLoggedIn = { newToken ->
                    token = newToken
                    TokenHolder.token = newToken
                    if (pendingDestinationValue != null) {
                        activeDestinationValue = pendingDestinationValue
                        pendingDestinationValue = null
                    }
                    scope.launch {
                        FichajeWidgetUpdater.updateAll(context.applicationContext)
                    }
                }
            )
        }
        activeDestination != null -> {
            QuickActionScreen(
                destination = activeDestination,
                onBack = { activeDestinationValue = null }
            )
        }
        else -> {
            DashboardScreen(
                repository = repository,
                onLoggedOut = {
                    token = null
                    TokenHolder.token = null
                    activeDestinationValue = null
                    pendingDestinationValue = null
                    scope.launch {
                        FichajeWidgetUpdater.updateAll(context.applicationContext)
                    }
                }
            )
        }
    }
}
