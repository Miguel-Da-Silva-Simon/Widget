package com.example.widget_android.data

import java.util.Calendar
import java.util.Locale

object AttendanceTimeUtils {

    /** Parsea "HH:mm" o "H:mm" del servidor como hoy en hora local. */
    fun parseTodayHmToEpochMs(time: String): Long? {
        val t = time.trim()
        val parts = t.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].take(2).toIntOrNull() ?: return null
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun formatClockHHmm(epochMs: Long): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = epochMs
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }
}
