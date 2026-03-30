package com.example.widget_android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockingModePolicyTest {

    @Test
    fun `canChangeMode is true before the first action`() {
        val state = ClockingState(
            currentStepIndex = 0,
            currentState = AttendanceState.NOT_STARTED,
            isFinished = false
        )

        assertTrue(state.canChangeMode())
    }

    @Test
    fun `canChangeMode is false once the workday has started`() {
        val state = ClockingState(
            currentStepIndex = 1,
            currentState = AttendanceState.WORKING,
            isFinished = false
        )

        assertFalse(state.canChangeMode())
    }

    @Test
    fun `canChangeMode is false after finishing the workday`() {
        val state = ClockingState(
            currentStepIndex = 5,
            currentState = AttendanceState.FINISHED,
            isFinished = true
        )

        assertFalse(state.canChangeMode())
    }
}
