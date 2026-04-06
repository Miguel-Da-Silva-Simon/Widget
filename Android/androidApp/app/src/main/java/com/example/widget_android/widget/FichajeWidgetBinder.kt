package com.example.widget_android.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.widget_android.MainActivity
import com.example.widget_android.R
import com.example.widget_android.data.AttendanceAction
import com.example.widget_android.data.AttendanceDurations
import com.example.widget_android.data.AttendanceState
import com.example.widget_android.data.AttendanceTimeUtils
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.ClockingActionBindings
import com.example.widget_android.data.ClockingState
import com.example.widget_android.data.PrefKeys
import com.example.widget_android.data.ProfilePhotoStorage
import com.example.widget_android.data.TokenHolder
import com.example.widget_android.data.appDataStore
import com.example.widget_android.data.resolveActionBindings
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

internal object FichajeWidgetBinder {

    private const val PI_PRIMARY = 301
    private const val PI_BREAK = 302
    private const val PI_MEAL = 303
    private const val PI_PROFILE = 304
    private const val PI_OPEN_APP = 305
    private const val NOOP_ACTION = "NOOP"
    private const val DEFAULT_USER_NAME = "Practicas"
    private const val INACTIVE_TIMER = "--:--:--"

    suspend fun build(context: Context): RemoteViews {
        val app = context.applicationContext
        val views = RemoteViews(app.packageName, R.layout.widget_fichaje)

        val token = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
        val userName = app.appDataStore.data.map { it[PrefKeys.USER_NAME] }.first().orEmpty()
        val profilePhotoUri = app.appDataStore.data.map { it[PrefKeys.PROFILE_PHOTO_URI] }.first().orEmpty()

        views.setTextViewText(R.id.widget_name, userName.ifBlank { DEFAULT_USER_NAME })
        applyProfilePhoto(views, profilePhotoUri)

        if (token.isNullOrBlank()) {
            applySignedOutState(views)
            bindOpenAppClicks(app, views)
            return views
        }

        TokenHolder.token = token
        val repo = ClockingApiRepository(app, widgetMode = true)
        var todayResult = repo.today()

        if (todayResult.exceptionOrNull().isUnauthorized()) {
            val freshToken = app.appDataStore.data.map { it[PrefKeys.TOKEN] }.first()
            if (!freshToken.isNullOrBlank() && freshToken != token) {
                TokenHolder.token = freshToken
                todayResult = repo.today()
            }
        }

        val state = todayResult.getOrNull()

        if (state == null) {
            val ex = todayResult.exceptionOrNull()
            if (ex.isUnauthorized()) {
                applyErrorState(views, "Abre la app")
                bindOpenAppClicks(app, views)
            } else if (ex.isNetworkError()) {
                applyErrorState(views, "Sin conexión")
                bindFallbackClicks(app, views)
            } else {
                applyErrorState(views, "Pulsa para fichar")
                bindFallbackClicks(app, views)
            }
            return views
        }

        val actions = state.resolveActionBindings()
        val primaryAction = resolvePrimaryAction(state, actions)
        val returnHint = resolveReturnHint(repo, state)

        applyTimer(views, state)
        applyStateChrome(views, state)
        applyReturnHint(views, returnHint)
        applyActions(views, state, actions, primaryAction)
        applyAccessibility(views, state, actions, primaryAction, returnHint)
        bindClicks(
            app,
            views,
            if (state.isFinished) {
                FichajeWidgetActionReceiver.WIDGET_ACTION_START_NEW_WORKDAY
            } else {
                primaryAction?.name ?: NOOP_ACTION
            },
            actions.breakAction?.name ?: NOOP_ACTION,
            actions.mealAction?.name ?: NOOP_ACTION
        )
        return views
    }

    private fun applySignedOutState(views: RemoteViews) {
        views.setTextViewText(R.id.widget_name, "Conecta la app")
        applyInactiveTimer(views)
        applyDefaultChrome(views)
        applyReturnHint(views, null)
        setActionAppearance(
            views,
            R.id.widget_icon_primary,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_primary_start_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_break_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_meal_dim
        )
        applyStaticAccessibility(
            views = views,
            timerDescription = "Widget inactivo. Conecta la app.",
            primaryDescription = "Entrada no disponible",
            breakDescription = "Descanso no disponible",
            mealDescription = "Comida no disponible"
        )
    }

    private fun applyErrorState(views: RemoteViews, label: String = "Abre la app") {
        views.setTextViewText(R.id.widget_name, label)
        applyInactiveTimer(views)
        applyDefaultChrome(views)
        applyReturnHint(views, null)
        setActionAppearance(
            views,
            R.id.widget_icon_primary,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_primary_start_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_break_dim
        )
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            R.drawable.bg_action_button_disabled,
            R.drawable.ic_widget_meal_dim
        )
        applyStaticAccessibility(
            views = views,
            timerDescription = "Widget inactivo. $label.",
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
                views.setInt(R.id.widget_action_group, "setBackgroundResource", android.R.color.transparent)
            }
            AttendanceState.MEAL_ACTIVE -> {
                applyDefaultChrome(views)
                views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_orange)
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
        actions: ClockingActionBindings,
        primaryAction: AttendanceAction?
    ) {
        val primaryIsExit = !state.isFinished && primaryAction == AttendanceAction.CLOCK_OUT
        val primaryEnabled = state.isFinished || primaryAction != null
        val primaryHighlighted =
            if (state.isFinished) true else state.nextAllowedAction == primaryAction
        setActionAppearance(
            views,
            R.id.widget_icon_primary,
            backgroundFor(primaryEnabled, primaryHighlighted),
            when {
                !primaryEnabled && primaryIsExit -> R.drawable.ic_widget_primary_stop_dim
                !primaryEnabled -> R.drawable.ic_widget_primary_start_dim
                primaryIsExit -> R.drawable.ic_widget_primary_stop_white
                else -> R.drawable.ic_widget_primary_start_white
            }
        )

        val breakEnabled = actions.breakEnabled
        val breakHighlighted = state.nextAllowedAction == actions.breakAction
        setActionAppearance(
            views,
            R.id.widget_icon_break,
            backgroundFor(breakEnabled, breakHighlighted),
            if (breakEnabled) R.drawable.ic_widget_break_white else R.drawable.ic_widget_break_dim
        )

        val mealEnabled = actions.mealEnabled
        val mealHighlighted = state.nextAllowedAction == actions.mealAction
        setActionAppearance(
            views,
            R.id.widget_icon_meal,
            backgroundFor(mealEnabled, mealHighlighted),
            if (mealEnabled) R.drawable.ic_widget_meal_white else R.drawable.ic_widget_meal_dim
        )
    }

    private fun applyAccessibility(
        views: RemoteViews,
        state: ClockingState,
        actions: ClockingActionBindings,
        primaryAction: AttendanceAction?,
        returnHint: String?
    ) {
        val primaryDescription =
            if (state.isFinished) {
                "Empezar nueva jornada"
            } else {
                when (primaryAction) {
                    AttendanceAction.CLOCK_IN -> "Entrada"
                    AttendanceAction.CLOCK_OUT -> "Salida"
                    else -> "Entrada no disponible"
                }
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
            timerDescription = buildString {
                append("Contador. ")
                append(statusTitle(state))
                if (!returnHint.isNullOrBlank()) {
                    append(". ")
                    append(returnHint)
                }
            },
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

    private fun applyProfilePhoto(views: RemoteViews, photoUri: String) {
        if (photoUri.isBlank()) {
            views.setImageViewResource(R.id.widget_profile_photo, R.drawable.ic_profile_placeholder)
            return
        }

        ProfilePhotoStorage.decodeBitmap(photoUri)?.let {
            views.setImageViewBitmap(R.id.widget_profile_photo, it)
            return
        }

        ProfilePhotoStorage.toDisplayUri(photoUri)?.let {
            views.setImageViewUri(R.id.widget_profile_photo, it)
            return
        }

        views.setImageViewResource(R.id.widget_profile_photo, R.drawable.ic_profile_placeholder)
    }

    private fun applyReturnHint(views: RemoteViews, text: String?) {
        if (text.isNullOrBlank()) {
            views.setViewVisibility(R.id.widget_return_time, View.GONE)
            views.setTextViewText(R.id.widget_return_time, "")
            views.setContentDescription(R.id.widget_return_time, "")
            return
        }

        views.setTextViewText(R.id.widget_return_time, text)
        views.setContentDescription(R.id.widget_return_time, text)
        views.setViewVisibility(R.id.widget_return_time, View.VISIBLE)
    }

    private fun applyDefaultChrome(views: RemoteViews) {
        views.setInt(R.id.widget_timer_block, "setBackgroundResource", R.drawable.bg_timer_capsule)
        views.setInt(R.id.widget_status_dot, "setBackgroundResource", R.drawable.bg_dot_gray)
        views.setInt(R.id.widget_action_group, "setBackgroundResource", android.R.color.transparent)
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

    private suspend fun resolveReturnHint(
        repo: ClockingApiRepository,
        state: ClockingState
    ): String? {
        val returnAtMs =
            when (state.currentState) {
                AttendanceState.BREAK_ACTIVE -> {
                    resolveStartMs(repo.readBreakStartMs(), state.lastActionTime)
                        ?.plus(AttendanceDurations.BREAK_MS)
                }
                AttendanceState.MEAL_ACTIVE -> {
                    resolveStartMs(repo.readMealStartMs(), state.lastActionTime)
                        ?.plus(AttendanceDurations.MEAL_MS)
                }
                else -> null
            }

        return returnAtMs?.let {
            "Vuelve ${AttendanceTimeUtils.formatClockHHmm(it)}"
        }
    }

    private fun resolveStartMs(storedStartMs: Long, fallbackTime: String): Long? =
        storedStartMs.takeIf { it > 0L }
            ?: AttendanceTimeUtils.parseTodayHmToEpochMs(fallbackTime)

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

    private fun resolvePrimaryAction(
        state: ClockingState,
        actions: ClockingActionBindings
    ): AttendanceAction? =
        actions.widgetPrimaryAction
            ?: state.nextAllowedAction?.takeIf {
                it == AttendanceAction.CLOCK_IN || it == AttendanceAction.CLOCK_OUT
            }

    internal fun shellPlaceholder(context: Context): RemoteViews {
        val app = context.applicationContext
        val views = RemoteViews(app.packageName, R.layout.widget_fichaje)
        applySignedOutState(views)
        bindOpenAppClicks(app, views)
        return views
    }

    internal fun bindOpenAppClicks(
        context: Context,
        views: RemoteViews
    ) {
        val app = context.applicationContext
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        val openAppPendingIntent =
            PendingIntent.getActivity(
                app,
                PI_OPEN_APP,
                Intent(app, MainActivity::class.java).apply {
                    setPackage(app.packageName)
                    action = "com.example.widget_android.widget.OPEN_APP"
                    data = Uri.parse("widget://${app.packageName}/open-app")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                },
                flags
            )

        views.setOnClickPendingIntent(R.id.widget_btn_primary, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_primary, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_break, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_break, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_meal, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_meal, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_brand_icon, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_name, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_return_time, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_timer_block, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_timer_panel, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_chronometer, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_profile, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_profile_photo, openAppPendingIntent)
    }

    private fun bindFallbackClicks(context: Context, views: RemoteViews) {
        bindClicks(
            context,
            views,
            AttendanceAction.CLOCK_IN.name,
            NOOP_ACTION,
            NOOP_ACTION
        )
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
                data = Uri.parse("widget://${app.packageName}/action/$requestCode/$extra")
                putExtra(FichajeWidgetActionReceiver.EXTRA_WIDGET_ACTION, extra)
            }
            return PendingIntent.getBroadcast(app, requestCode, intent, flags)
        }

        val primaryPendingIntent = pi(primaryAction, PI_PRIMARY)
        val breakPendingIntent = pi(breakAction, PI_BREAK)
        val mealPendingIntent = pi(mealAction, PI_MEAL)
        val profilePendingIntent =
            PendingIntent.getActivity(
                app,
                PI_PROFILE,
                Intent(app, WidgetProfilePhotoPickerActivity::class.java).apply {
                    setPackage(app.packageName)
                    action = "com.example.widget_android.widget.PICK_PROFILE_PHOTO"
                    data = Uri.parse("widget://${app.packageName}/profile-photo")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                flags
            )

        views.setOnClickPendingIntent(R.id.widget_btn_primary, primaryPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_primary, primaryPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_break, breakPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_break, breakPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_meal, mealPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_icon_meal, mealPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_btn_profile, profilePendingIntent)
        views.setOnClickPendingIntent(R.id.widget_profile_photo, profilePendingIntent)
    }

    private fun statusTitle(state: ClockingState): String =
        when (state.currentState) {
            AttendanceState.NOT_STARTED -> "Listo para fichar"
            AttendanceState.WORKING -> "Estas trabajando"
            AttendanceState.BREAK_ACTIVE -> "Estas de descanso"
            AttendanceState.MEAL_ACTIVE -> "Estas en comida"
            AttendanceState.FINISHED -> "Jornada finalizada"
        }

    private fun Throwable?.isUnauthorized(): Boolean =
        this is HttpException && code() == 401

    private fun Throwable?.isNetworkError(): Boolean =
        this is SocketTimeoutException || this is ConnectException || this is UnknownHostException
}
