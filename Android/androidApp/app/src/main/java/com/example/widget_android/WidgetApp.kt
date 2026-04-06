package com.example.widget_android

import android.app.Application
import com.example.widget_android.data.SessionRepository
import com.example.widget_android.data.TokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetApp : Application() {

    /** Trabajo en segundo plano (widget, IO) sin depender del tiempo de vida del BroadcastReceiver. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            val stored = SessionRepository(this@WidgetApp).readToken()
            if (!stored.isNullOrBlank()) {
                TokenHolder.token = stored
            }
        }
    }
}
