package com.example.familytreeplatform.feature.home

import com.example.familytreeplatform.models.ChangeLog
import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenTest {
    @Test
    fun summaryReflectsFamilyDataAndContributors() {
        val people = listOf(
            person("1", "Budi", "ALIVE", "1985-04-12"),
            person("2", "Siti", "ALIVE", null),
            person("3", "Hadi", "UNKNOWN", null)
        )
        val logs = listOf(
            ChangeLog("a", "2026-01-01", "u1", "PERSON", "UPDATE", null),
            ChangeLog("b", "2026-01-02", "u2", "PERSON", "UPDATE", null),
            ChangeLog("c", "2026-01-03", "u1", "RELATIONSHIP", "CREATE", null)
        )

        val summary = homeSummary(people, logs, pendingSync = 2)

        assertEquals(3, summary.people)
        assertEquals(2, summary.living)
        assertEquals(2, summary.contributors)
        assertEquals(2, summary.pendingSync)
        assertEquals(2, summary.missingBirthDate)
        assertEquals(1, summary.unknownLifeStatus)
    }

    @Test
    fun greetingUsesFirstName() {
        assertEquals("Budi", homeFirstName("Budi Santoso"))
        assertEquals("keluarga", homeFirstName(" "))
    }

    private fun person(id: String, name: String, status: String, birthDate: String?) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = status,
        birthDate = birthDate
    )
}
