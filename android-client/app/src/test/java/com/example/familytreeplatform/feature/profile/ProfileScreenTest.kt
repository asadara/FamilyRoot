package com.example.familytreeplatform.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileScreenTest {
    @Test
    fun `profile initials use the first two words`() {
        assertEquals("BS", profileInitials("  Budi   Santoso  "))
        assertEquals("F", profileInitials("Father"))
        assertEquals("FR", profileInitials("  "))
    }
}
