package com.example.widget_android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object FichajeWidgetUpdater {

    private const val BIND_TIMEOUT_MS = 20_000L

    fun updateAll(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val manager = AppWidgetManager.getInstance(app)
            val cn = ComponentName(app, FichajeAppWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(cn)
            if (ids.isEmpty()) return@launch
            val views = withTimeoutOrNull(BIND_TIMEOUT_MS) { FichajeWidgetBinder.build(app) }
                ?: return@launch
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }
}
