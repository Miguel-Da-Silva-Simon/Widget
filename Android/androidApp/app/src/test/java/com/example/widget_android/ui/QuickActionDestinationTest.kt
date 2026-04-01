package com.example.widget_android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickActionDestinationTest {

    @Test
    fun fromValue_returnsMatchingDestination() {
        val destination = QuickActionDestination.fromValue("new_invoice")

        assertEquals(QuickActionDestination.NEW_INVOICE, destination)
    }

    @Test
    fun fromValue_returnsNullForUnknownValue() {
        val destination = QuickActionDestination.fromValue("unknown")

        assertNull(destination)
    }

    @Test
    fun values_areUnique() {
        val values = QuickActionDestination.entries.map { it.value }

        assertEquals(values.size, values.toSet().size)
    }
}
