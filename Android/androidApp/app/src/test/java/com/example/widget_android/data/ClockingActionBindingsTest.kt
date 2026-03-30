package com.example.widget_android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockingActionBindingsTest {

    @Test
    fun `resolveActionBindings maps clock in for a fresh day`() {
        val state = ClockingState(
            currentState = AttendanceState.NOT_STARTED,
            enabledActions = setOf(AttendanceAction.CLOCK_IN),
            nextAllowedAction = AttendanceAction.CLOCK_IN
        )

        val bindings = state.resolveActionBindings()

        assertEquals(AttendanceAction.CLOCK_IN, bindings.clockInAction)
        assertEquals(AttendanceAction.CLOCK_IN, bindings.widgetPrimaryAction)
        assertNull(bindings.clockOutAction)
        assertNull(bindings.breakAction)
        assertNull(bindings.mealAction)
        assertTrue(bindings.clockInEnabled)
        assertFalse(bindings.clockOutEnabled)
    }

    @Test
    fun `resolveActionBindings keeps break and meal actions aligned with enabled actions`() {
        val state = ClockingState(
            currentState = AttendanceState.WORKING,
            enabledActions = setOf(
                AttendanceAction.BREAK_START,
                AttendanceAction.MEAL_START,
                AttendanceAction.CLOCK_OUT
            ),
            nextAllowedAction = AttendanceAction.BREAK_START
        )

        val bindings = state.resolveActionBindings()

        assertEquals(AttendanceAction.CLOCK_OUT, bindings.widgetPrimaryAction)
        assertEquals(AttendanceAction.CLOCK_OUT, bindings.clockOutAction)
        assertEquals(AttendanceAction.BREAK_START, bindings.breakAction)
        assertEquals(AttendanceAction.MEAL_START, bindings.mealAction)
        assertTrue(bindings.breakEnabled)
        assertTrue(bindings.mealEnabled)
    }

    @Test
    fun `resolveActionBindings switches to end actions while on an active pause`() {
        val state = ClockingState(
            currentState = AttendanceState.BREAK_ACTIVE,
            enabledActions = setOf(AttendanceAction.BREAK_END),
            nextAllowedAction = AttendanceAction.BREAK_END
        )

        val bindings = state.resolveActionBindings()

        assertEquals(AttendanceAction.BREAK_END, bindings.breakAction)
        assertNull(bindings.widgetPrimaryAction)
        assertFalse(bindings.clockInEnabled)
        assertFalse(bindings.clockOutEnabled)
        assertTrue(bindings.breakEnabled)
        assertFalse(bindings.mealEnabled)
    }
}
