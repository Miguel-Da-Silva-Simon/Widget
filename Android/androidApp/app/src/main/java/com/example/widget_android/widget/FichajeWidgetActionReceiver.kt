package com.example.widget_android.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.widget_android.WidgetApp
import com.example.widget_android.data.AttendanceAction
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.ClockingState
import com.example.widget_android.data.PrefKeys
import com.example.widget_android.data.TokenHolder
import com.example.widget_android.data.appDataStore
import com.example.widget_android.network.toUserMessage
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Taps del widget: el trabajo va a [WidgetApp.applicationScope] y el broadcast termina al instante
 * (evita el limite ~10s de goAsync en Android 14+).
 */
class FichajeWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CLICK) return
        val raw = intent.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
        val action = AttendanceAction.entries.firstOrNull { it.name == raw } ?: return
        val app = context.applicationContext
        val scope =
            (app as? WidgetApp)?.applicationScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                processAction(app, action)
            } finally {
                FichajeWidgetUpdater.updateAll(app)
            }
        }
    }

    private suspend fun processAction(app: Context, action: AttendanceAction) {
        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        if (token.isNullOrBlank()) {
            toast(app, "Inicia sesi\u00f3n en la app para usar el widget")
            return
        }
        TokenHolder.token = token
        val repo = ClockingApiRepository(app)
        val result = runWithRetry { repo.doAction(action) }
        result.onFailure { toast(app, it.toUserMessage()) }
    }

    private suspend fun runWithRetry(block: suspend () -> Result<ClockingState>): Result<ClockingState> {
        var result = block()
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            if (error is SocketTimeoutException || error is ConnectException) {
                delay(500)
                result = block()
            }
        }
        return result
    }

    private suspend fun toast(app: Context, msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_CLICK = "com.example.widget_android.WIDGET_CLICK"
        const val EXTRA_WIDGET_ACTION = "widget_action"
    }
}
