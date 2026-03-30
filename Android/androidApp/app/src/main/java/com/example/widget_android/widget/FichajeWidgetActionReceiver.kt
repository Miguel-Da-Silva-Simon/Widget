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
 * (evita el límite ~10s de goAsync en Android 14+).
 */
class FichajeWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CLICK) return
        val raw = intent.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
        val app = context.applicationContext
        val scope =
            (app as? WidgetApp)?.applicationScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                processAction(app, raw)
            } finally {
                FichajeWidgetUpdater.updateAll(app)
            }
        }
    }

    private suspend fun processAction(app: Context, raw: String) {
        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        if (token.isNullOrBlank()) {
            toast(app, "Inicia sesión en la app para usar el widget")
            return
        }
        TokenHolder.token = token
        val repo = ClockingApiRepository(app)

        when (raw) {
            A_CLOCK_IN -> {
                val r = runWithRetry { repo.doAction(AttendanceAction.CLOCK_IN) }
                r.onFailure { toast(app, it.toUserMessage()) }
            }
            A_CLOCK_OUT -> {
                val r = runWithRetry { repo.doAction(AttendanceAction.CLOCK_OUT) }
                r.onFailure { toast(app, it.toUserMessage()) }
            }
            A_BREAK -> {
                val a = resolveBreak(repo)
                if (a == null) {
                    toast(app, "No hay acción de descanso disponible ahora")
                    return
                }
                val r = runWithRetry { repo.doAction(a) }
                r.onFailure { toast(app, it.toUserMessage()) }
            }
            A_MEAL -> {
                val a = resolveMeal(repo)
                if (a == null) {
                    toast(app, "No hay acción de comida disponible ahora")
                    return
                }
                val r = runWithRetry { repo.doAction(a) }
                r.onFailure { toast(app, it.toUserMessage()) }
            }
        }
    }

    private suspend fun runWithRetry(block: suspend () -> Result<ClockingState>): Result<ClockingState> {
        var r = block()
        if (r.isFailure) {
            val e = r.exceptionOrNull()
            if (e is SocketTimeoutException || e is ConnectException) {
                delay(500)
                r = block()
            }
        }
        return r
    }

    private suspend fun resolveBreak(repo: ClockingApiRepository): AttendanceAction? {
        val state = runWithRetry { repo.today() }.getOrNull() ?: return null
        return when {
            state.enabledActions.contains(AttendanceAction.BREAK_START) -> AttendanceAction.BREAK_START
            state.enabledActions.contains(AttendanceAction.BREAK_END) -> AttendanceAction.BREAK_END
            else -> null
        }
    }

    private suspend fun resolveMeal(repo: ClockingApiRepository): AttendanceAction? {
        val state = runWithRetry { repo.today() }.getOrNull() ?: return null
        return when {
            state.enabledActions.contains(AttendanceAction.MEAL_START) -> AttendanceAction.MEAL_START
            state.enabledActions.contains(AttendanceAction.MEAL_END) -> AttendanceAction.MEAL_END
            else -> null
        }
    }

    private suspend fun toast(app: Context, msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_CLICK = "com.example.widget_android.WIDGET_CLICK"
        const val EXTRA_WIDGET_ACTION = "widget_action"

        const val A_CLOCK_IN = "CLOCK_IN"
        const val A_CLOCK_OUT = "CLOCK_OUT"
        const val A_BREAK = "BREAK"
        const val A_MEAL = "MEAL"
    }
}
