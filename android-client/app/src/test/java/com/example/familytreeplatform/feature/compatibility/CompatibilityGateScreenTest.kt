package com.example.familytreeplatform.feature.compatibility

import com.example.familytreeplatform.models.AppCompatibilityResponse
import com.example.familytreeplatform.models.AppCompatibilityState
import com.example.familytreeplatform.models.CompatibilityGateStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityGateScreenTest {
    private val updateResponse = AppCompatibilityResponse(
        status = "UPDATE_AVAILABLE",
        blocking = false,
        message = "Versi aplikasi yang lebih baru tersedia.",
        channel = "PILOT",
        minimumSupportedVersionCode = 1,
        latestVersionCode = 2,
        backendApiContractVersion = 1,
        updateUrl = "https://example.test/update",
        checkedAt = "2026-07-28T00:00:00Z"
    )

    @Test
    fun `supported update warns once and can continue for current session`() {
        val warning = AppCompatibilityState(
            status = CompatibilityGateStatus.UPDATE_AVAILABLE,
            response = updateResponse
        )

        assertTrue(compatibilityRequiresGate(warning))
        assertFalse(
            compatibilityRequiresGate(
                warning.copy(updateWarningAcknowledged = true)
            )
        )
        assertEquals("Pembaruan tersedia", compatibilityTitle(warning))
    }

    @Test
    fun `only enforced incompatible builds stay blocked`() {
        val blocked = AppCompatibilityState(
            status = CompatibilityGateStatus.BLOCKED,
            response = updateResponse.copy(
                status = "APP_TOO_OLD",
                blocking = true,
                enforcementEnabled = true,
                message = "Versi aplikasi ini sudah tidak didukung."
            )
        )
        val unavailable = AppCompatibilityState(
            status = CompatibilityGateStatus.UNAVAILABLE,
            error = "Failed to connect to 127.0.0.1"
        )

        assertTrue(compatibilityRequiresGate(blocked))
        assertTrue(compatibilityRequiresGate(unavailable))
        assertFalse(
            compatibilityRequiresGate(
                unavailable.copy(updateWarningAcknowledged = true)
            )
        )
        assertEquals("Aplikasi perlu diperbarui", compatibilityTitle(blocked))
        assertFalse(compatibilityMessage(unavailable).contains("127.0.0.1"))
    }

    @Test
    fun `non enforced mismatch is a warning that can be continued`() {
        val warning = AppCompatibilityState(
            status = CompatibilityGateStatus.UPDATE_AVAILABLE,
            response = updateResponse.copy(
                status = "APP_TOO_NEW",
                blocking = false,
                enforcementEnabled = false,
                message = "Backend belum mendukung versi aplikasi ini."
            )
        )

        assertTrue(compatibilityRequiresGate(warning))
        assertFalse(
            compatibilityRequiresGate(
                warning.copy(updateWarningAcknowledged = true)
            )
        )
        assertEquals("Build aplikasi belum terdaftar", compatibilityTitle(warning))
    }
}
