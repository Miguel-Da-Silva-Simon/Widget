package com.example.widget_android.data

enum class ClockingMode {
    WITH_MEAL,
    TWO_BREAKS
}

enum class StepType {
    ENTRY,
    BREAK1_START,
    BREAK1_END,
    MEAL_START,
    MEAL_END,
    BREAK2_START,
    BREAK2_END,
    EXIT
}

enum class AttendanceAction {
    CLOCK_IN,
    BREAK_START,
    BREAK_END,
    MEAL_START,
    MEAL_END,
    CLOCK_OUT
}

enum class AttendanceState {
    NOT_STARTED,
    WORKING,
    BREAK_ACTIVE,
    MEAL_ACTIVE,
    FINISHED
}

data class ClockingStep(
    val type: StepType,
    val label: String
)

data class ClockingState(
    val mode: ClockingMode = ClockingMode.WITH_MEAL,
    val currentStepIndex: Int = 0,
    val isFinished: Boolean = false,
    val lastActionLabel: String = "Sin fichajes todavía",
    val lastActionTime: String = "--:--",
    val nextStepLabel: String = "No disponible",
    val currentState: AttendanceState = AttendanceState.NOT_STARTED,
    val nextAllowedAction: AttendanceAction? = null,
    val enabledActions: Set<AttendanceAction> = emptySet(),
    /** Segundos desde entrada hasta ahora (o hasta salida); incluye descanso y comida. */
    val elapsedSeconds: Long = 0
)