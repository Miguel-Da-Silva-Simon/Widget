package com.example.widget_android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import com.example.widget_android.WidgetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FichajeAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: android.content.Intent?) {
        if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
            if (ids.isEmpty()) {
                super.onReceive(context, intent)
                return
            }
            val pendingResult: BroadcastReceiver.PendingResult = goAsync()
            val app = context.applicationContext
            val scope = (app as? WidgetApp)?.applicationScope
                ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val manager = AppWidgetManager.getInstance(app)
            scope.launch(Dispatchers.IO) {
                try {
                    val shell = FichajeWidgetBinder.shellPlaceholder(app)
                    ids.forEach { id -> manager.updateAppWidget(id, shell) }

                    val full = tryBuild(app)
                    if (full != null) {
                        ids.forEach { id -> manager.updateAppWidget(id, full) }
                    }
                } catch (_: Throwable) {
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Handled entirely in onReceive with goAsync()
    }

    private suspend fun tryBuild(app: Context): android.widget.RemoteViews? {
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val views = withTimeoutOrNull(WIDGET_BIND_TIMEOUT_MS) {
                    FichajeWidgetBinder.build(app)
                }
                if (views != null) return views
            } catch (_: Throwable) {
            }
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS)
        }
        return null
    }

    companion object {
        private const val WIDGET_BIND_TIMEOUT_MS = 12_000L
        private const val RETRY_DELAY_MS = 2_000L
        private const val MAX_ATTEMPTS = 2
    }
}
