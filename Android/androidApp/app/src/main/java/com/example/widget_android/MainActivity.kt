package com.example.widget_android

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
import com.example.widget_android.widget.FichajeWidgetUpdater
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WidgetandroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val appCtx = remember { context.applicationContext }
    val repository = remember { ClockingApiRepository(appCtx) }
    val session = remember { SessionRepository(appCtx) }
    var token by remember { mutableStateOf<String?>(TokenHolder.token) }
    var bootstrapped by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        var restored = repository.restoreSession()
        if (restored.isFailure) {
            val e = restored.exceptionOrNull()
            if (e is SocketTimeoutException || e is ConnectException) {
                delay(250)
                restored = repository.restoreSession()
            }
        }
        token = session.readToken()
        TokenHolder.token = token
        bootstrapped = true
        var lastWidgetToken: String? = null
        session.authTokenFlow.collectLatest { t ->
            token = t
            TokenHolder.token = t
            val changed = t != lastWidgetToken
            lastWidgetToken = t
            if (changed) {
                FichajeWidgetUpdater.updateAll(context.applicationContext)
            }
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
                    scope.launch {
                        FichajeWidgetUpdater.updateAll(context.applicationContext)
                    }
                }
            )
        }
        else -> {
            DashboardScreen(
                repository = repository,
                onLoggedOut = {
                    token = null
                    TokenHolder.token = null
                    scope.launch {
                        FichajeWidgetUpdater.updateAll(context.applicationContext)
                    }
                }
            )
        }
    }
}
