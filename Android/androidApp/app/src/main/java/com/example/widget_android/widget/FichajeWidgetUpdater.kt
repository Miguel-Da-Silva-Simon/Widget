package com.example.widget_android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.widget_android.WidgetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object FichajeWidgetUpdater {

    private const val BIND_TIMEOUT_MS = 12_000L
    private val updateMutex = Mutex()
    private val pendingUpdate = AtomicBoolean(false)

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val scope = (app as? WidgetApp)?.applicationScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

        if (updateMutex.isLocked) {
            pendingUpdate.set(true)
            return
        }

        scope.launch(Dispatchers.IO) {
            doUpdate(app)
        }
    }

    private suspend fun doUpdate(app: Context) {
        updateMutex.withLock {
            pendingUpdate.set(false)
            try {
                val manager = AppWidgetManager.getInstance(app)
                val cn = ComponentName(app, FichajeAppWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(cn)
                if (ids.isEmpty()) return
                val views = withTimeoutOrNull(BIND_TIMEOUT_MS) { FichajeWidgetBinder.build(app) }
                if (views != null) {
                    ids.forEach { id -> manager.updateAppWidget(id, views) }
                }
            } catch (_: Throwable) {
                // build failed — widget keeps its current state
            }
        }
        if (pendingUpdate.getAndSet(false)) {
            doUpdate(app)
        }
    }
}
