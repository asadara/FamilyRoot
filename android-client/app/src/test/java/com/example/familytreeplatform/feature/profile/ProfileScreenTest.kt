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

    @Test
    fun `notification history uses concise localized labels`() {
        assertEquals("Berhasil", notificationKindLabel("SUCCESS"))
        assertEquals("Perlu perhatian", notificationKindLabel("WARNING"))
        assertEquals("Gagal", notificationKindLabel("ERROR"))
        assertEquals("Informasi", notificationKindLabel("INFO"))
        assertEquals(
            "2026-07-28 09:46",
            notificationTimeLabel("2026-07-28T09:46:13.000Z")
        )
    }
}
