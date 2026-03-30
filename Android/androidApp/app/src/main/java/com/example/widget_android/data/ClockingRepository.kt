package com.example.widget_android.data

import android.content.Context
import com.example.widget_android.widget.FichajeWidgetUpdater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ClockingRepository {

    private const val PREFS_NAME = "clocking_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_STEP_INDEX = "step_index"
    private const val KEY_FINISHED = "finished"
    private const val KEY_LAST_ACTION_LABEL = "last_action_label"
    private const val KEY_LAST_ACTION_TIME = "last_action_time"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSteps(mode: ClockingMode): List<ClockingStep> {
        return when (mode) {
            ClockingMode.WITH_MEAL -> listOf(
                ClockingStep(StepType.ENTRY, "Entrada"),
                ClockingStep(StepType.BREAK1_START, "Inicio descanso"),
                ClockingStep(StepType.BREAK1_END, "Fin descanso"),
                ClockingStep(StepType.MEAL_START, "Inicio comida"),
                ClockingStep(StepType.MEAL_END, "Fin comida"),
                ClockingStep(StepType.EXIT, "Salida")
            )
            ClockingMode.TWO_BREAKS -> listOf(
                ClockingStep(StepType.ENTRY, "Entrada"),
                ClockingStep(StepType.BREAK1_START, "Inicio descanso 1"),
                ClockingStep(StepType.BREAK1_END, "Fin descanso 1"),
                ClockingStep(StepType.BREAK2_START, "Inicio descanso 2"),
                ClockingStep(StepType.BREAK2_END, "Fin descanso 2"),
                ClockingStep(StepType.EXIT, "Salida")
            )
        }
    }

    fun getState(context: Context): ClockingState {
        val prefs = prefs(context)

        val mode = when (prefs.getString(KEY_MODE, ClockingMode.WITH_MEAL.name)) {
            ClockingMode.TWO_BREAKS.name -> ClockingMode.TWO_BREAKS
            else -> ClockingMode.WITH_MEAL
        }

        val finished = prefs.getBoolean(KEY_FINISHED, false)
        val stepIndex = prefs.getInt(KEY_STEP_INDEX, 0)
        val steps = getSteps(mode)
        val nextLabel =
            if (finished) {
                "Jornada terminada"
            } else {
                steps.getOrNull(stepIndex)?.label ?: "No disponible"
            }

        return ClockingState(
            mode = mode,
            currentStepIndex = stepIndex,
            isFinished = finished,
            lastActionLabel = prefs.getString(KEY_LAST_ACTION_LABEL, "Sin fichajes todavía").orEmpty(),
            lastActionTime = prefs.getString(KEY_LAST_ACTION_TIME, "--:--").orEmpty(),
            nextStepLabel = nextLabel
        )
    }

    fun setMode(context: Context, mode: ClockingMode) {
        val current = getState(context)
        prefs(context).edit()
            .putString(KEY_MODE, mode.name)
            .putInt(KEY_STEP_INDEX, 0)
            .putBoolean(KEY_FINISHED, false)
            .putString(KEY_LAST_ACTION_LABEL, "Modo cambiado")
            .putString(KEY_LAST_ACTION_TIME, currentTime())
            .apply()

        CoroutineScope(Dispatchers.Main).launch {
            FichajeWidgetUpdater.updateAll(context)
        }
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .putInt(KEY_STEP_INDEX, 0)
            .putBoolean(KEY_FINISHED, false)
            .putString(KEY_LAST_ACTION_LABEL, "Jornada reiniciada")
            .putString(KEY_LAST_ACTION_TIME, currentTime())
            .apply()

        CoroutineScope(Dispatchers.Main).launch {
            FichajeWidgetUpdater.updateAll(context)
        }
    }

    fun getCurrentStep(context: Context): ClockingStep? {
        val state = getState(context)
        val steps = getSteps(state.mode)
        if (state.isFinished) return null
        return steps.getOrNull(state.currentStepIndex)
    }

    fun performNextClocking(context: Context) {
        val state = getState(context)
        if (state.isFinished) return

        val steps = getSteps(state.mode)
        val currentStep = steps.getOrNull(state.currentStepIndex) ?: return

        val nextIndex = state.currentStepIndex + 1
        val finished = nextIndex >= steps.size

        prefs(context).edit()
            .putInt(KEY_STEP_INDEX, if (finished) state.currentStepIndex else nextIndex)
            .putBoolean(KEY_FINISHED, finished)
            .putString(KEY_LAST_ACTION_LABEL, currentStep.label)
            .putString(KEY_LAST_ACTION_TIME, currentTime())
            .apply()

        CoroutineScope(Dispatchers.Main).launch {
            FichajeWidgetUpdater.updateAll(context)
        }
    }

    private fun currentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}