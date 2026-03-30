package com.example.widget_android.network

import com.example.widget_android.data.ClockingMode

data class LoginRequestDto(val email: String, val password: String)

data class LoginResponseDto(
    val token: String,
    val sessionId: Long,
    val expiresAt: String,
    val user: UserDto
)

data class SessionResponseDto(
    /** Si Gson no encuentra el campo, no debe tratarse como "no autenticado" (evita false por defecto). */
    val authenticated: Boolean? = null,
    val sessionId: Long,
    val expiresAt: String,
    val user: UserDto
)

data class UserDto(val id: Long, val name: String, val email: String)

data class ClockingStateDto(
    val mode: ClockingMode,
    val currentStepIndex: Int,
    val finished: Boolean,
    val lastActionLabel: String,
    val lastActionTime: String,
    val nextStepLabel: String,
    val currentState: String? = null,
    val nextAllowedAction: String? = null,
    val enabledActions: List<String> = emptyList(),
    val lastEventType: String? = null,
    val elapsedSeconds: Long = 0
)

data class SetModeRequestDto(val mode: ClockingMode)

data class AttendanceActionRequestDto(val action: String)
