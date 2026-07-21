package com.example.familytreeplatform.feature.spaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceSelectionScreenTest {
    @Test
    fun familySpaceLabelsHideBackendCodes() {
        assertEquals("Pemilik silsilah", familySpaceRoleLabel("OWNER"))
        assertEquals("Kontributor", familySpaceRoleLabel("EDITOR"))
        assertFalse(familySpaceRoleLabel("VIEWER").contains("VIEWER"))
    }

    @Test
    fun initialsUseAtMostTwoWords() {
        assertEquals("KD", familySpaceInitials("Keluarga Demo Besar"))
        assertEquals("TR", familySpaceInitials(""))
    }

    @Test
    fun invitationFailuresExplainNextAction() {
        assertTrue(spaceSelectionErrorMessage("HTTP 404 NOT_FOUND: Invitation not found").contains("tidak ditemukan"))
        assertTrue(spaceSelectionErrorMessage("HTTP 400 VALIDATION_ERROR: Invitation has expired").contains("kedaluwarsa"))
        assertTrue(spaceSelectionErrorMessage("HTTP 503 INTERNAL_ERROR: unavailable").contains("Server sedang"))
    }
}
