package com.example.familytreeplatform.feature.spacesettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceSettingsScreenTest {
    @Test
    fun rolesAndReviewStatusesUseUserLanguage() {
        assertEquals("Pembaca", invitationRoleLabel("VIEWER"))
        assertEquals("Kontributor", invitationRoleLabel("EDITOR"))
        assertEquals("Menunggu", reviewStatusLabel("PENDING"))
        assertFalse(invitationRoleLabel("ADMIN").contains("ADMIN"))
    }

    @Test
    fun connectionDetailsAreNotExposed() {
        val message = settingsErrorMessage("Failed to connect to 127.0.0.1:3001")
        assertTrue(message.contains("Periksa koneksi"))
        assertFalse(message.contains("127.0.0.1"))
    }
}
