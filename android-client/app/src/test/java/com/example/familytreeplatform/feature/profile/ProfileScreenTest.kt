package com.example.familytreeplatform.feature.profile

import com.example.familytreeplatform.models.UserNotificationItem
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

    @Test
    fun `profile only renders ten latest notifications`() {
        val notifications = (1..25).map { index ->
            UserNotificationItem(
                notificationId = "notification-$index",
                kind = "INFO",
                code = "TEST",
                title = "Notification $index",
                message = "Message $index",
                createdAt = "2026-07-30T10:00:00Z"
            )
        }

        assertEquals(10, recentProfileNotifications(notifications).size)
        assertEquals("notification-1", recentProfileNotifications(notifications).first().notificationId)
    }
}
