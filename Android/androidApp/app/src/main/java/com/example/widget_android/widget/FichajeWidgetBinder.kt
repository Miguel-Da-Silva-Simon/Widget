package com.example.widget_android.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import com.example.widget_android.R
import com.example.widget_android.data.AttendanceAction
import com.example.widget_android.data.AttendanceState
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.ClockingActionBindings
import com.example.widget_android.data.ClockingState
import com.example.widget_android.data.PrefKeys
import com.example.widget_android.data.TokenHolder
import com.example.widget_android.data.appDataStore
import com.example.widget_android.data.resolveActionBindings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal object FichajeWidgetBinder {

    private const val PI_PRIMARY = 301
    private const val PI_BREAK = 302
    private const val PI_MEAL = 303
    private const val NOOP_ACTION = "NOOP"
    private const val DEFAULT_USER_NAME = "Practicas"
    private const val INACTIVE_TIMER = "--:--:--"

    suspend fun build(context: Context): RemoteViews {
        val app = context.applicationContext
        val views = RemoteViews(app.packageName, R.layout.widget_fichaje)

        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        val userName = app.appDataStore.data.map { it[PrefKeys.USER_NAME] }.first().orEmpty()

        views.setTextViewText(R.id.widget_name, userName.ifBlank { DEFAULT_USER_NAME })

        if (token.isNullOrBlank()) {
            applySignedOutState(views)
            bindClicks(app, views, NOOP_ACTION, NOOP_ACTION, NOOP_ACTION)
            return views
        }

        TokenHolder.token = token
        val repo = ClockingApiRepository(app)
        val state = runCatching { repo.today().getOrNull() }.getOrNull()

        if (state == null) {
            applyErrorState(views)
            bindClicks(app, views, NOOP_ACTION, NOOP_ACTION, NOOP_ACTION)
            return views
        }

        val actions = state.resolveActionBindings()

        applyTimer(views, state)
        applyStateChrome(views, state)
        applyActions(views, state, actions)
        applyAccessibility(views, state, actions)
        bindClicks(
            app,
            views,
            actions.widgetPrimaryAction?.name ?: NOOP_ACTION,
            actions.breakAction?.name ?: NOOP_ACTION,
            actions.mealAction?.name ?: NOOP_ACTION
        )
        return views
    }

    private fun applySignedOutState(views: RemoteViews) {
        views.setTextViewText(R.id.widget_name, "Conecta la app")
        applyInactiveTimer(views)
        applyDefaultChrome(views)
        setActionAppearance(
            views,
            R.id.widget_icon_primary,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_entrada_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_descanso_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_comida_dim
        )
        applyStaticAccessibility(
            views = views,
            timerDescription = "Widget inactivo. Conecta la app.",
            primaryDescription = "Entrada no disponible",
            breakDescription = "Descanso no disponible",
            mealDescription = "Comida no disponible"
        )
    }

    private fun applyErrorState(views: RemoteViews) {
        views.setTextViewText(R.id.widget_name, "Abre la app")
        applyInactiveTimer(views)
        applyDefaultChrome(views)
        setActionAppearance(
            views,
            R.id.widget_icon_primary,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_entrada_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_descanso_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_comida_dim
        )
        applyStaticAccessibility(
            views = views,
            timerDescription = "Widget inactivo. Abre la app para sincronizar.",
            primaryDescription = "Entrada no disponible",
            breakDescription = "Descanso no disponible",
            mealDescription = "Comida no disponible"
        )
    }

    private fun applyTimer(views: RemoteViews, state: ClockingState) {
        when {
            state.isFinished -> {
                val base = SystemClock.elapsedRealtime() - state.elapsedSeconds * 1000
                views.setChronometer(R.id.widget_chronometer, base, null, false)
            }
            state.currentState == AttendanceState.NOT_STARTED -> {
                views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), null, false)
            }
            else -> {
                val base = SystemClock.elapsedRealtime() - state.elapsedSeconds * 1000
                views.setChronometer(R.id.widget_chronometer, base, null, true)
            }
        }
    }

    private fun applyStateChrome(views: RemoteViews, state: ClockingState) {
        when (state.currentState) {
            AttendanceState.BREAK_ACTIVE -> {
                views.setInt(R.id.widget_timer_panel, "setBackgroundResource", R.drawable.bg_timer_capsule_break)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_amber)
                views.setInt(R.id.widget_action_group, "setBackgroundResource", R.drawable.bg_action_cluster_break)
            }
            AttendanceState.MEAL_ACTIVE -> {
                views.setInt(R.id.widget_timer_panel, "setBackgroundResource", R.drawable.bg_timer_capsule_meal)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_orange)
                views.setInt(R.id.widget_action_group, "setBackgroundResource", R.drawable.bg_action_cluster)
            }
            AttendanceState.WORKING -> {
                applyDefaultChrome(views)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_green)
            }
            else -> applyDefaultChrome(views)
        }
    }

    private fun applyActions(
        views: RemoteViews,
        state: ClockingState,
        actions: ClockingActionBindings
    ) {
        val primaryIsExit = actions.widgetPrimaryAction == AttendanceAction.CLOCK_OUT || state.isFinished
        val primaryEnabled = actions.widgetPrimaryAction != null
        val primaryHighlighted = state.nextAllowedAction == actions.widgetPrimaryAction
        setActionAppearance(
            views,
            R.id.widget_icon_primary,
            backgroundFor(primaryEnabled, primaryHighlighted),
            when {
                !primaryEnabled && primaryIsExit -> R.drawable.ic_widget_salida_dim
                !primaryEnabled -> R.drawable.ic_widget_entrada_dim
                primaryIsExit -> R.drawable.ic_widget_salida_white
                else -> R.drawable.ic_widget_entrada_white
            }
        )

        val breakEnabled = actions.breakEnabled
        val breakHighlighted = state.nextAllowedAction == actions.breakAction
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            backgroundFor(breakEnabled, breakHighlighted),
            if (breakEnabled) R.drawable.ic_widget_descanso_white else R.drawable.ic_widget_descanso_dim
        )

        val mealEnabled = actions.mealEnabled
        val mealHighlighted = state.nextAllowedAction == actions.mealAction
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            backgroundFor(mealEnabled, mealHighlighted),
            if (mealEnabled) R.drawable.ic_widget_comida_white else R.drawable.ic_widget_comida_dim
        )
    }

    private fun applyAccessibility(
        views: RemoteViews,
        state: ClockingState,
        actions: ClockingActionBindings
    ) {
        val primaryDescription =
            when (actions.widgetPrimaryAction) {
                AttendanceAction.CLOCK_IN -> "Entrada"
                AttendanceAction.CLOCK_OUT -> "Salida"
                else -> if (state.isFinished) "Salida no disponible" else "Entrada no disponible"
            }
        val breakDescription =
            when (actions.breakAction) {
                AttendanceAction.BREAK_START -> "Iniciar descanso"
                AttendanceAction.BREAK_END -> "Finalizar descanso"
                else -> "Descanso no disponible"
            }
        val mealDescription =
            when (actions.mealAction) {
                AttendanceAction.MEAL_START -> "Iniciar comida"
                AttendanceAction.MEAL_END -> "Finalizar comida"
                else -> "Comida no disponible"
            }

        applyStaticAccessibility(
            views = views,
            timerDescription = "Contador. " + statusTitle(state),
            primaryDescription = primaryDescription,
            breakDescription = breakDescription,
            mealDescription = mealDescription
        )
    }

    private fun applyStaticAccessibility(
        views: RemoteViews,
        timerDescription: String,
        primaryDescription: String,
        breakDescription: String,
        mealDescription: String
    ) {
        views.setContentDescription(R.id.widget_timer_block, timerDescription)
        views.setContentDescription(R.id.widget_chronometer, timerDescription)
        setButtonDescription(views, R.id.widget_btn_primary, R.id.widget_icon_primary, primaryDescription)
        setButtonDescription(views, R.id.widget_btn_break, R.id.widget_icon_break, breakDescription)
        setButtonDescription(views, R.id.widget_btn_meal, R.id.widget_icon_meal, mealDescription)
    }

    private fun applyInactiveTimer(views: RemoteViews) {
        views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), null, false)
        views.setTextViewText(R.id.widget_chronometer, INACTIVE_TIMER)
    }

    private fun applyDefaultChrome(views: RemoteViews) {
        views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule)
        views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_gray)
        views.setInt(R.id.widget_action_group, "setBackgroundResource", R.drawable.bg_action_cluster)
    }

    private fun setActionAppearance(
        views: RemoteViews,
        iconId: Int,
        backgroundId: Int,
        iconRes: Int
    ) {
        views.setInt(iconId, "setBackgroundResource", backgroundId)
        views.setImageViewResource(iconId, iconRes)
    }

    private fun setButtonDescription(
        views: RemoteViews,
        buttonId: Int,
        iconId: Int,
        description: String
    ) {
        views.setContentDescription(buttonId, description)
        views.setContentDescription(iconId, description)
    }

    private fun backgroundFor(enabled: Boolean, highlighted: Boolean): Int =
        when {
            !enabled -> R.drawable.bg_action_button_disabled
            highlighted -> R.drawable.bg_action_button_active
            else -> R.drawable.bg_action_button_enabled
        }

    internal fun shellPlaceholder(context: Context): RemoteViews {
        val app = context.applicationContext
        val views = RemoteViews(app.packageName, R.layout.widget_fichaje)
        applySignedOutState(views)
        bindClicks(app, views, NOOP_ACTION, NOOP_ACTION, NOOP_ACTION)
        return views
    }

    internal fun bindClicks(
        context: Context,
        views: RemoteViews,
        primaryAction: String,
        breakAction: String,
        mealAction: String
    ) {
        val app = context.applicationContext
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        fun pi(extra: String, requestCode: Int): PendingIntent {
            val intent = Intent(app, FichajeWidgetActionReceiver::class.java).apply {
                setPackage(app.packageName)
                action = FichajeWidgetActionReceiver.ACTION_CLICK
                putExtra(FichajeWidgetActionReceiver.EXTRA_WIDGET_ACTION, extra)
            }
            return PendingIntent.getBroadcast(app, requestCode, intent, flags)
        }

        val primaryPendingIntent = pi(primaryAction, PI_PRIMARY)
        val breakPendingIntent = pi(breakAction, PI_BREAK)
        val mealPendingIntent = pi(mealAction, PI_MEAL)

        views.setOnClickPendingIntent(R.id.widget_btn_primary, primaryPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_primary, primaryPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_break, breakPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_break, breakPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_meal, mealPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_meal, mealPendingIntent)
    }

    private fun statusTitle(state: ClockingState): String =
        when (state.currentState) {
            AttendanceState.NOT_STARTED -> "Listo para fichar"
            AttendanceState.WORKING -> "Estas trabajando"
            AttendanceState.BREAK_ACTIVE -> "Estas de descanso"
            AttendanceState.MEAL_ACTIVE -> "Estas en comida"
            AttendanceState.FINISHED -> "Jornada finalizada"
        }
}
