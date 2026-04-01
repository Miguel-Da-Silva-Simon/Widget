package com.example.widget_android.data

import android.content.Context
import com.example.widget_android.network.ApiClient
import com.example.widget_android.network.AttendanceActionRequestDto
import com.example.widget_android.network.ClockingStateDto
import com.example.widget_android.network.LoginRequestDto
import com.example.widget_android.network.SetModeRequestDto
import retrofit2.HttpException

class ClockingApiRepository(context: Context) {

    private val app = context.applicationContext
    private val api = ApiClient.service(app)
    private val session = SessionRepository(app)

    suspend fun login(email: String, password: String): Result<String> =
        runCatching {
            val response = api.login(LoginRequestDto(email.trim(), password))
            session.clearEntryStart()
            session.clearBreakAndMeal()
            session.saveSession(
                token = response.token,
                sessionId = response.sessionId,
                expiresAt = response.expiresAt,
                userName = response.user.name,
                userEmail = response.user.email
            )
            response.token
        }

    suspend fun restoreSession(): Result<Boolean> {
        return try {
            val token = session.readToken()
            if (token.isNullOrBlank()) {
                Result.success(false)
            } else {
                TokenHolder.token = token
                val current = api.session()
                // Solo cerrar si el servidor envía authenticated == false (no el falso por defecto de Gson).
                if (current.authenticated == false) {
                    session.clearSession()
                    Result.success(false)
                } else {
                    session.saveSession(
                        token = token,
                        sessionId = current.sessionId,
                        expiresAt = current.expiresAt,
                        userName = current.user.name,
                        userEmail = current.user.email
                    )
                    Result.success(true)
                }
            }
        } catch (e: HttpException) {
            if (e.code() == 401) session.clearSession()
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun refreshMe(): Result<String> =
        runCatching { api.me().name }
            .onSuccess { session.saveUserName(it) }

    suspend fun today(): Result<ClockingState> =
        runCatching {
            val dto = api.today()
            applyTodayEntryRules(dto)
            syncLocalBreakMealWithServer(dto)
            dto.toState()
        }

    suspend fun next(): Result<ClockingState> =
        runCatching {
            val dto = api.next()
            if (dto.lastActionLabel == "Entrada") {
                session.saveEntryStartMs(System.currentTimeMillis())
            }
            if (dto.finished) {
                session.clearEntryStart()
            }
            dto.toState()
        }

    suspend fun doAction(action: AttendanceAction): Result<ClockingState> =
        runCatching {
            val dto = api.action(AttendanceActionRequestDto(action.name))
            if (dto.lastActionLabel == "Entrada") {
                session.saveEntryStartMs(System.currentTimeMillis())
            }
            if (dto.finished) {
                session.clearEntryStart()
            }
            when (action) {
                AttendanceAction.BREAK_START -> session.saveBreakStartMs(System.currentTimeMillis())
                AttendanceAction.BREAK_END -> session.clearBreakStart()
                AttendanceAction.MEAL_START -> session.saveMealStartMs(System.currentTimeMillis())
                AttendanceAction.MEAL_END -> session.clearMealStart()
                AttendanceAction.CLOCK_OUT -> session.clearBreakAndMeal()
                else -> Unit
            }
            syncLocalBreakMealWithServer(dto)
            dto.toState()
        }

    suspend fun reset(): Result<ClockingState> =
        runCatching {
            val dto = api.reset()
            session.clearEntryStart()
            session.clearBreakAndMeal()
            dto.toState()
        }

    suspend fun setMode(mode: ClockingMode): Result<ClockingState> =
        runCatching {
            val dto = api.setMode(SetModeRequestDto(mode))
            session.clearEntryStart()
            session.clearBreakAndMeal()
            dto.toState()
        }

    suspend fun logout() {
        runCatching { api.logout() }
        session.clearSession()
    }

    suspend fun readUserName(): String? = session.readUserName()

    suspend fun readProfilePhotoUri(): String? = session.readProfilePhotoUri()

    suspend fun saveProfilePhotoUri(uri: String) {
        session.saveProfilePhotoUri(uri)
    }

    suspend fun clearProfilePhotoUri() {
        session.clearProfilePhotoUri()
    }

    suspend fun readEntryStartMs(): Long = session.readEntryStartMs()

    suspend fun readBreakStartMs(): Long = session.readBreakStartMs()

    suspend fun readMealStartMs(): Long = session.readMealStartMs()

    private suspend fun applyTodayEntryRules(dto: ClockingStateDto) {
        when {
            dto.finished -> {
                session.clearEntryStart()
                session.clearBreakAndMeal()
            }
            dto.currentStepIndex == 0 &&
                (
                    dto.currentState == AttendanceState.NOT_STARTED.name ||
                        dto.lastEventType == null
                    ) -> {
                session.clearEntryStart()
                session.clearBreakAndMeal()
            }
        }
    }

    private suspend fun syncLocalBreakMealWithServer(dto: ClockingStateDto) {
        val state = dto.currentState?.let { AttendanceState.valueOf(it) } ?: return
        when (state) {
            AttendanceState.WORKING, AttendanceState.NOT_STARTED, AttendanceState.FINISHED -> {
                session.clearBreakAndMeal()
            }
            AttendanceState.BREAK_ACTIVE -> {
                session.clearMealStart()
                if (session.readBreakStartMs() <= 0L) {
                    val ms = AttendanceTimeUtils.parseTodayHmToEpochMs(dto.lastActionTime)
                        ?: System.currentTimeMillis()
                    session.saveBreakStartMs(ms)
                }
            }
            AttendanceState.MEAL_ACTIVE -> {
                session.clearBreakStart()
                if (session.readMealStartMs() <= 0L) {
                    val ms = AttendanceTimeUtils.parseTodayHmToEpochMs(dto.lastActionTime)
                        ?: System.currentTimeMillis()
                    session.saveMealStartMs(ms)
                }
            }
        }
    }

    private fun ClockingStateDto.toState(): ClockingState =
        ClockingState(
            mode = mode,
            currentStepIndex = currentStepIndex,
            isFinished = finished,
            lastActionLabel = lastActionLabel,
            lastActionTime = lastActionTime,
            nextStepLabel = nextStepLabel,
            currentState = currentState?.let { AttendanceState.valueOf(it) } ?: AttendanceState.NOT_STARTED,
            nextAllowedAction = nextAllowedAction?.let { AttendanceAction.valueOf(it) },
            enabledActions = enabledActions.map { AttendanceAction.valueOf(it) }.toSet(),
            elapsedSeconds = elapsedSeconds
        )
}
