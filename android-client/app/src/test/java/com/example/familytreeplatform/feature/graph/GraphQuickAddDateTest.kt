package com.example.familytreeplatform.feature.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphQuickAddDateTest {
    @Test
    fun `relationship date is displayed in Indonesian style`() {
        assertEquals("01 Januari 1990", formatIndonesianDate("1990-01-01"))
        assertEquals("17 Agustus 1945", formatIndonesianDate("1945-08-17"))
    }

    @Test
    fun `invalid ISO dates are not formatted`() {
        assertNull(formatIndonesianDate("1990-13-01"))
        assertNull(formatIndonesianDate("1990-02-31"))
        assertNull(formatIndonesianDate("01-01-1990"))
    }
}
