package com.example.widget_android.data

data class ClockingActionBindings(
    val clockInAction: AttendanceAction? = null,
    val clockOutAction: AttendanceAction? = null,
    val breakAction: AttendanceAction? = null,
    val mealAction: AttendanceAction? = null
) {
    val clockInEnabled: Boolean
        get() = clockInAction != null

    val clockOutEnabled: Boolean
        get() = clockOutAction != null

    val breakEnabled: Boolean
        get() = breakAction != null

    val mealEnabled: Boolean
        get() = mealAction != null

    val widgetPrimaryAction: AttendanceAction?
        get() = clockOutAction ?: clockInAction
}

fun ClockingState.resolveActionBindings(): ClockingActionBindings {
    val enabled = enabledActions
    return ClockingActionBindings(
        clockInAction = AttendanceAction.CLOCK_IN.takeIf { enabled.contains(it) },
        clockOutAction = AttendanceAction.CLOCK_OUT.takeIf { enabled.contains(it) },
        breakAction =
            when {
                enabled.contains(AttendanceAction.BREAK_START) -> AttendanceAction.BREAK_START
                enabled.contains(AttendanceAction.BREAK_END) -> AttendanceAction.BREAK_END
                else -> null
            },
        mealAction =
            when {
                enabled.contains(AttendanceAction.MEAL_START) -> AttendanceAction.MEAL_START
                enabled.contains(AttendanceAction.MEAL_END) -> AttendanceAction.MEAL_END
                else -> null
            }
    )
}
