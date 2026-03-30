package com.example.widget_android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WidgetApp : Application() {

    /** Trabajo en segundo plano (widget, IO) sin depender del tiempo de vida del BroadcastReceiver. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // No leer DataStore aquí para rellenar TokenHolder: la primera emisión puede ser
        // anterior al login y sobrescribir el token que ya puso MainActivity / saveSession (401 en API).
    }
}
