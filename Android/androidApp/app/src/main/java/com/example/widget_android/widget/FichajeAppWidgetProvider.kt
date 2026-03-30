package com.example.widget_android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.example.widget_android.WidgetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Actualización del widget en [WidgetApp.applicationScope] sin [goAsync]: en Android 14+ el broadcast
 * debe terminar rápido; el bind pesado sigue en segundo plano.
 */
class FichajeAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val app = context.applicationContext
        val scope =
            (app as? WidgetApp)?.applicationScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            try {
                val shell = FichajeWidgetBinder.shellPlaceholder(app)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, shell)
                }
                val full = withTimeoutOrNull(WIDGET_BIND_TIMEOUT_MS) {
                    FichajeWidgetBinder.build(app)
                }
                if (full != null) {
                    appWidgetIds.forEach { id ->
                        appWidgetManager.updateAppWidget(id, full)
                    }
                }
            } catch (_: Throwable) {
                val fallback = FichajeWidgetBinder.shellPlaceholder(app)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, fallback)
                }
            }
        }
    }

    companion object {
        private const val WIDGET_BIND_TIMEOUT_MS = 20_000L
    }
}
