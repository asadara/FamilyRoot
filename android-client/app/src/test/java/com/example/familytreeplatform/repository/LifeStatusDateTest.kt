package com.example.familytreeplatform.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LifeStatusDateTest {
    @Test
    fun `empty deceased date becomes null for legacy queued mutations`() {
        assertNull(normalizeLifeStatusDate("DECEASED", ""))
        assertNull(normalizeLifeStatusDate("DECEASED", "   "))
    }

    @Test
    fun `non deceased status always clears deceased date`() {
        assertNull(normalizeLifeStatusDate("ALIVE", "2020-01-02"))
    }

    @Test
    fun `valid deceased date is trimmed and retained`() {
        assertEquals(
            "2020-01-02",
            normalizeLifeStatusDate("DECEASED", " 2020-01-02 ")
        )
    }

    @Test
    fun `malformed deceased date is rejected before reaching backend`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeLifeStatusDate("DECEASED", "02-01-2020")
        }
    }
}
