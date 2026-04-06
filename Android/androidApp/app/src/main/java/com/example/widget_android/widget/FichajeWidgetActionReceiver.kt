package com.example.widget_android.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.widget_android.MainActivity
import com.example.widget_android.WidgetApp
import com.example.widget_android.data.AttendanceAction
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.ClockingState
import com.example.widget_android.data.PrefKeys
import com.example.widget_android.data.TokenHolder
import com.example.widget_android.data.appDataStore
import com.example.widget_android.network.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/**
 * Mantiene vivo el broadcast hasta terminar la acción para que los taps del widget no se pierdan
 * cuando el proceso de la app aún no está activo.
 */
class FichajeWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CLICK) return
        val raw = intent.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
        val app = context.applicationContext
        val pendingResult = goAsync()
        val scope =
            (app as? WidgetApp)?.applicationScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            try {
                val action = when (raw) {
                    WIDGET_ACTION_START_NEW_WORKDAY -> null
                    NOOP_ACTION -> null
                    else -> AttendanceAction.entries.firstOrNull { it.name == raw }
                }

                when {
                    raw == WIDGET_ACTION_START_NEW_WORKDAY -> startNewWorkday(app)
                    raw == NOOP_ACTION -> { /* skip, just refresh widget below */ }
                    action != null -> processAction(app, action)
                    else -> { /* unknown action, just refresh widget */ }
                }
            } catch (t: Throwable) {
                toast(app, t.toUserMessage())
            } finally {
                refreshWidgetSync(app)
                pendingResult.finish()
            }
        }
    }

    private suspend fun processAction(app: Context, action: AttendanceAction) {
        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        if (token.isNullOrBlank()) {
            openApp(app)
            return
        }
        TokenHolder.token = token
        val repo = ClockingApiRepository(app, widgetMode = true)
        val result = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
            runAction { repo.doAction(action) }
        }
        if (result == null) {
            toast(app, "Sin respuesta del servidor")
            return
        }
        if (result.exceptionOrNull().isUnauthorized()) {
            val freshToken = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
            if (!freshToken.isNullOrBlank() && freshToken != token) {
                TokenHolder.token = freshToken
                val retry = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    runAction { repo.doAction(action) }
                }
                if (retry != null && retry.isSuccess) return
                retry?.exceptionOrNull()?.let { handleActionFailure(app, it) }
                return
            }
        }
        result.exceptionOrNull()?.let { handleActionFailure(app, it) }
    }

    private suspend fun startNewWorkday(app: Context) {
        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        if (token.isNullOrBlank()) {
            openApp(app)
            return
        }
        TokenHolder.token = token
        val repo = ClockingApiRepository(app, widgetMode = true)
        var resetResult = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
            runAction { repo.reset() }
        }
        if (resetResult == null) {
            toast(app, "Sin respuesta del servidor")
            return
        }
        if (resetResult.exceptionOrNull().isUnauthorized()) {
            val freshToken = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
            if (!freshToken.isNullOrBlank() && freshToken != token) {
                TokenHolder.token = freshToken
                resetResult = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    runAction { repo.reset() }
                }
                if (resetResult == null) {
                    toast(app, "Sin respuesta del servidor")
                    return
                }
            }
        }
        if (resetResult.isFailure) {
            resetResult.exceptionOrNull()?.let { handleActionFailure(app, it) }
            return
        }

        val clockInResult = withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
            runAction { repo.doAction(AttendanceAction.CLOCK_IN) }
        }
        if (clockInResult == null) {
            toast(app, "Sin respuesta del servidor")
            return
        }
        clockInResult.exceptionOrNull()?.let { handleActionFailure(app, it) }
    }

    private suspend fun runAction(block: suspend () -> Result<ClockingState>): Result<ClockingState> =
        block()

    private suspend fun toast(app: Context, msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun handleActionFailure(app: Context, throwable: Throwable) {
        if (throwable.isUnauthorized()) {
            toast(app, "Error de sesión. Abre la app y reintenta.")
            return
        }
        toast(app, throwable.toUserMessage())
    }

    private suspend fun openApp(app: Context) {
        withContext(Dispatchers.Main) {
            app.startActivity(
                Intent(app, MainActivity::class.java).apply {
                    setPackage(app.packageName)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        }
    }

    private suspend fun refreshWidgetSync(app: Context) {
        try {
            val manager = AppWidgetManager.getInstance(app)
            val cn = ComponentName(app, FichajeAppWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(cn)
            if (ids.isEmpty()) return
            val views = withTimeoutOrNull(WIDGET_REFRESH_TIMEOUT_MS) {
                FichajeWidgetBinder.build(app)
            }
            if (views != null) {
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        } catch (_: Throwable) {
            // widget stays with current state
        }
    }

    private fun Throwable?.isUnauthorized(): Boolean =
        this is HttpException && code() == 401

    companion object {
        const val ACTION_CLICK = "com.example.widget_android.WIDGET_CLICK"
        const val EXTRA_WIDGET_ACTION = "widget_action"
        const val WIDGET_ACTION_START_NEW_WORKDAY = "START_NEW_WORKDAY"
        internal const val NOOP_ACTION = "NOOP"
        private const val RECEIVER_TIMEOUT_MS = 10_000L
        private const val WIDGET_REFRESH_TIMEOUT_MS = 12_000L
    }
}
