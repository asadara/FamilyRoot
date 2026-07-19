package com.example.familytreeplatform.feature.spaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpaceSelectionScreenTest {
    @Test
    fun familySpaceLabelsHideBackendCodes() {
        assertEquals("Pemilik ruang", familySpaceRoleLabel("OWNER"))
        assertEquals("Kontributor", familySpaceRoleLabel("EDITOR"))
        assertFalse(familySpaceRoleLabel("VIEWER").contains("VIEWER"))
    }

    @Test
    fun initialsUseAtMostTwoWords() {
        assertEquals("KD", familySpaceInitials("Keluarga Demo Besar"))
        assertEquals("FR", familySpaceInitials(""))
    }
}
