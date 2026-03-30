package com.example.widget_android.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.widget_android.R
import com.example.widget_android.data.AttendanceAction
import com.example.widget_android.data.AttendanceDurations
import com.example.widget_android.data.AttendanceState
import com.example.widget_android.data.AttendanceTimeUtils
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.ClockingMode
import com.example.widget_android.data.ClockingState
import com.example.widget_android.data.PrefKeys
import com.example.widget_android.data.TokenHolder
import com.example.widget_android.data.appDataStore
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal object FichajeWidgetBinder {

    private const val PI_PRIMARY = 301
    private const val PI_BREAK = 302
    private const val PI_MEAL = 303
    private const val NOOP_ACTION = "NOOP"

    suspend fun build(context: Context): RemoteViews {
        val app = context.applicationContext
        val views = RemoteViews(app.packageName, R.layout.widget_fichaje)

        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        val userName = app.appDataStore.data.map { it[PrefKeys.USER_NAME] }.first().orEmpty()
        val breakStartMs = app.appDataStore.data.map { it[PrefKeys.BREAK_START_MS] }.first() ?: -1L
        val mealStartMs = app.appDataStore.data.map { it[PrefKeys.MEAL_START_MS] }.first() ?: -1L

        views.setTextViewText(R.id.widget_name, userName.ifBlank { "Practicas" })

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

        applyTimer(views, state)
        applySummary(views, state, breakStartMs, mealStartMs)
        applyActions(views, state)
        bindClicks(
            app,
            views,
            primaryActionExtra(state),
            if (supportsBreak(state)) FichajeWidgetActionReceiver.A_BREAK else NOOP_ACTION,
            if (supportsMeal(state)) FichajeWidgetActionReceiver.A_MEAL else NOOP_ACTION
        )
        return views
    }

    private fun applySignedOutState(views: RemoteViews) {
        views.setTextViewText(R.id.widget_name, "Conecta la app")
        views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), null, false)
        views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule)
        views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_gray)
        views.setTextViewText(R.id.widget_status_title, "Inicia sesion")
        views.setTextViewText(R.id.widget_status_subtitle, "Usa la app para activar el widget")
        views.setViewVisibility(R.id.widget_return_hint, View.GONE)
        views.setTextViewText(R.id.widget_metric_last_value, "--")
        views.setTextViewText(R.id.widget_metric_next_value, "Entrar")
        views.setTextViewText(R.id.widget_metric_mode_value, "--")
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
    }

    private fun applyErrorState(views: RemoteViews) {
        views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), null, false)
        views.setTextViewText(R.id.widget_chronometer, "--:--:--")
        views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule)
        views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_gray)
        views.setTextViewText(R.id.widget_status_title, "No se pudo actualizar")
        views.setTextViewText(R.id.widget_status_subtitle, "Abre la app para volver a sincronizar")
        views.setViewVisibility(R.id.widget_return_hint, View.GONE)
        views.setTextViewText(R.id.widget_metric_last_value, "--")
        views.setTextViewText(R.id.widget_metric_next_value, "Reintentar")
        views.setTextViewText(R.id.widget_metric_mode_value, "--")
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

    private fun applySummary(
        views: RemoteViews,
        state: ClockingState,
        breakStartMs: Long,
        mealStartMs: Long
    ) {
        views.setTextViewText(R.id.widget_status_title, statusTitle(state))
        views.setTextViewText(R.id.widget_status_subtitle, statusSubtitle(state))
        views.setTextViewText(R.id.widget_metric_last_value, lastMetric(state))
        views.setTextViewText(R.id.widget_metric_next_value, nextMetric(state))
        views.setTextViewText(R.id.widget_metric_mode_value, modeMetric(state.mode))

        when (state.currentState) {
            AttendanceState.BREAK_ACTIVE -> {
                views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule_break)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_amber)
                if (breakStartMs > 0L) {
                    views.setTextViewText(
                        R.id.widget_return_hint,
                        "Vuelves " + AttendanceTimeUtils.formatClockHHmm(breakStartMs + AttendanceDurations.BREAK_MS)
                    )
                    views.setViewVisibility(R.id.widget_return_hint, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_return_hint, View.GONE)
                }
            }
            AttendanceState.MEAL_ACTIVE -> {
                views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule_meal)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_orange)
                if (mealStartMs > 0L) {
                    views.setTextViewText(
                        R.id.widget_return_hint,
                        "Vuelves " + AttendanceTimeUtils.formatClockHHmm(mealStartMs + AttendanceDurations.MEAL_MS)
                    )
                    views.setViewVisibility(R.id.widget_return_hint, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_return_hint, View.GONE)
                }
            }
            AttendanceState.WORKING -> {
                views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_green)
                views.setViewVisibility(R.id.widget_return_hint, View.GONE)
            }
            else -> {
                views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_gray)
                views.setViewVisibility(R.id.widget_return_hint, View.GONE)
            }
        }
    }

    private fun applyActions(views: RemoteViews, state: ClockingState) {
        val primaryIsExit = state.enabledActions.contains(AttendanceAction.CLOCK_OUT)
        val primaryEnabled = primaryIsExit || state.enabledActions.contains(AttendanceAction.CLOCK_IN)
        val primaryHighlighted = state.nextAllowedAction == AttendanceAction.CLOCK_OUT ||
            state.nextAllowedAction == AttendanceAction.CLOCK_IN
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

        val breakEnabled = supportsBreak(state)
        val breakHighlighted = state.nextAllowedAction == AttendanceAction.BREAK_START ||
            state.nextAllowedAction == AttendanceAction.BREAK_END
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            backgroundFor(breakEnabled, breakHighlighted),
            if (breakEnabled) R.drawable.ic_widget_descanso_white else R.drawable.ic_widget_descanso_dim
        )

        val mealEnabled = supportsMeal(state)
        val mealHighlighted = state.nextAllowedAction == AttendanceAction.MEAL_START ||
            state.nextAllowedAction == AttendanceAction.MEAL_END
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            backgroundFor(mealEnabled, mealHighlighted),
            if (mealEnabled) R.drawable.ic_widget_comida_white else R.drawable.ic_widget_comida_dim
        )
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

        views.setOnClickPendingIntent(R.id.widget_btn_primary, pi(primaryAction, PI_PRIMARY))
        views.setOnClickPendingIntent(R.id.widget_btn_break, pi(breakAction, PI_BREAK))
        views.setOnClickPendingIntent(R.id.widget_btn_meal, pi(mealAction, PI_MEAL))
    }

    private fun supportsBreak(state: ClockingState): Boolean =
        state.enabledActions.contains(AttendanceAction.BREAK_START) ||
            state.enabledActions.contains(AttendanceAction.BREAK_END)

    private fun supportsMeal(state: ClockingState): Boolean =
        state.enabledActions.contains(AttendanceAction.MEAL_START) ||
            state.enabledActions.contains(AttendanceAction.MEAL_END)

    private fun primaryActionExtra(state: ClockingState): String =
        when {
            state.enabledActions.contains(AttendanceAction.CLOCK_OUT) -> FichajeWidgetActionReceiver.A_CLOCK_OUT
            state.enabledActions.contains(AttendanceAction.CLOCK_IN) -> FichajeWidgetActionReceiver.A_CLOCK_IN
            else -> NOOP_ACTION
        }

    private fun statusTitle(state: ClockingState): String =
        when (state.currentState) {
            AttendanceState.NOT_STARTED -> "Listo para fichar"
            AttendanceState.WORKING -> "Estas trabajando"
            AttendanceState.BREAK_ACTIVE -> "Estas de descanso"
            AttendanceState.MEAL_ACTIVE -> "Estas en comida"
            AttendanceState.FINISHED -> "Jornada finalizada"
        }

    private fun statusSubtitle(state: ClockingState): String =
        when (state.currentState) {
            AttendanceState.NOT_STARTED -> "Sin actividad hoy"
            AttendanceState.FINISHED -> "Jornada cerrada " + formatHoursMinutes(state.elapsedSeconds)
            else -> "Jornada actual " + formatHoursMinutes(state.elapsedSeconds)
        }

    private fun lastMetric(state: ClockingState): String =
        if (state.lastActionTime == "--:--") {
            "--"
        } else {
            shortLabel(state.lastActionLabel) + " " + state.lastActionTime
        }

    private fun nextMetric(state: ClockingState): String =
        if (state.isFinished) {
            "Completa"
        } else {
            shortLabel(state.nextStepLabel)
        }

    private fun modeMetric(mode: ClockingMode): String =
        when (mode) {
            ClockingMode.WITH_MEAL -> "Con comida"
            ClockingMode.TWO_BREAKS -> "2 descansos"
        }

    private fun shortLabel(label: String): String =
        when (label) {
            "Inicio descanso", "Inicio descanso 1", "Inicio descanso 2" -> "Descanso"
            "Fin descanso", "Fin descanso 1", "Fin descanso 2" -> "Fin descanso"
            "Inicio comida" -> "Comida"
            "Fin comida" -> "Fin comida"
            "Jornada terminada" -> "Completa"
            else -> label
        }

    private fun formatHoursMinutes(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return String.format(Locale.getDefault(), "%02dh %02dm", hours, minutes)
    }
}
