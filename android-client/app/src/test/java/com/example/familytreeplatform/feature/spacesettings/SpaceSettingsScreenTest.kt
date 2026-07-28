package com.example.familytreeplatform.feature.spacesettings

import com.example.familytreeplatform.models.ProposalItem
import com.example.familytreeplatform.models.SpaceMember
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
    fun invalidInvitationEmailIsActionable() {
        assertTrue(
            settingsErrorMessage("targetEmail must be an email").contains("email penerima")
        )
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

    @Test
    fun proposalComparisonShowsCurrentValueAndHandlesEmptyData() {
        val proposal = ProposalItem(
            proposalId = "proposal-2",
            spaceId = "space-1",
            personId = "person-1",
            field = "notes",
            proposedValue = "Catatan usulan",
            beforeValue = "Catatan awal",
            currentValue = "Catatan terbaru",
            status = "PENDING",
            createdAt = "2026-07-28T00:00:00Z",
            personName = "Budi Santoso"
        )

        assertEquals("Catatan terbaru", proposalCurrentValueLabel(proposal))
        assertEquals(
            "Belum diisi",
            proposalCurrentValueLabel(proposal.copy(currentValue = null))
        )
    }

    @Test
    fun proposalRejectionRequiresAUsefulBoundedReason() {
        assertFalse(isProposalRejectionReasonValid(""))
        assertFalse(isProposalRejectionReasonValid("   "))
        assertTrue(isProposalRejectionReasonValid("Data belum didukung sumber"))
        assertTrue(isProposalRejectionReasonValid("a".repeat(1000)))
        assertFalse(isProposalRejectionReasonValid("a".repeat(1001)))
    }

    @Test
    fun proposalCommentRequiresNonBlankBoundedContext() {
        assertFalse(isProposalCommentValid(""))
        assertFalse(isProposalCommentValid("   "))
        assertTrue(isProposalCommentValid("Mohon cek sumber keluarga"))
        assertTrue(isProposalCommentValid("a".repeat(1000)))
        assertFalse(isProposalCommentValid("a".repeat(1001)))
    }

    @Test
    fun membershipManagementRespectsOwnerAndAdminBoundaries() {
        val editor = member(role = "EDITOR")
        val admin = member(role = "ADMIN")
        val owner = member(role = "OWNER")

        assertEquals(
            listOf("ADMIN", "EDITOR", "VIEWER"),
            manageableRoles("OWNER", editor)
        )
        assertEquals(listOf("EDITOR", "VIEWER"), manageableRoles("ADMIN", editor))
        assertTrue(manageableRoles("ADMIN", admin).isEmpty())
        assertTrue(manageableRoles("OWNER", owner).isEmpty())
        assertTrue(
            manageableRoles("OWNER", editor.copy(isCurrentUser = true)).isEmpty()
        )
    }

    private fun member(role: String) = SpaceMember(
        memberId = "member-$role",
        userId = "user-$role",
        displayName = role,
        role = role,
        joinedAt = "2026-07-28T00:00:00Z"
    )
}
