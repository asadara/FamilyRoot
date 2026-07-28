package com.example.familytreeplatform.feature.spacesettings

import com.example.familytreeplatform.models.ProposalItem
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

    @Test
    fun permissionFailuresAreActionable() {
        assertTrue(settingsErrorMessage("HTTP 403 FORBIDDEN: Role VIEWER is not allowed").contains("tidak memiliki izin"))
        assertTrue(settingsErrorMessage("HTTP 500 INTERNAL_ERROR: database unavailable").contains("Server sedang"))
    }

    @Test
    fun deletionProposalUsesSafeUserFacingLanguage() {
        val proposal = ProposalItem(
            proposalId = "proposal-1",
            spaceId = "space-1",
            personId = "person-1",
            field = "DELETE_PERSON",
            proposedValue = "REQUEST_DELETE",
            status = "PENDING",
            createdAt = "2026-07-28T00:00:00Z",
            personName = "Budi Santoso"
        )

        assertEquals("Permintaan penghapusan person", proposalFieldLabel(proposal.field))
        assertFalse(proposalValueLabel(proposal).contains("REQUEST_DELETE"))
    }
}
