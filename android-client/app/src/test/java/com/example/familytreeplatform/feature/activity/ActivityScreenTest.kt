package com.example.familytreeplatform.feature.activity

import com.example.familytreeplatform.models.ChangeLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityScreenTest {
    @Test
    fun `activity presentation translates backend codes`() {
        val presentation = activityPresentation(log("PERSON", "UPDATE"))

        assertEquals("Data person diperbarui", presentation.title)
        assertEquals(ActivityGroup.PERSON, presentation.group)
    }

    @Test
    fun `collaboration filter isolates collective actions`() {
        assertTrue(ActivityFilter.COLLABORATION.matches(log("CLAIM", "VERIFY")))
        assertFalse(ActivityFilter.COLLABORATION.matches(log("RELATIONSHIP", "CREATE")))
    }

    @Test
    fun `summary counts unique contributors and formats timestamp`() {
        val logs = listOf(log("PERSON", "CREATE", "user-1"), log("CLAIM", "VERIFY", "user-1"), log("SPOUSE", "CREATE", "user-2"))

        assertEquals(ActivitySummary(total = 3, contributors = 2), activitySummary(logs))
        assertEquals("2026-07-19 · 14:25", activityDateLabel("2026-07-19T14:25:31Z"))
    }

    @Test
    fun `technical network errors are not exposed to users`() {
        assertEquals(
            "Riwayat belum dapat dimuat. Periksa koneksi lalu coba segarkan kembali.",
            activityErrorMessage("unexpected end of stream on http://127.0.0.1:3001/")
        )
    }

    private fun log(type: String, operation: String, actor: String = "user-1") = ChangeLog(
        changeId = "$type-$operation-$actor",
        createdAt = "2026-07-19T14:25:31Z",
        actorUserId = actor,
        entityType = type,
        operation = operation,
        note = null
    )
}
