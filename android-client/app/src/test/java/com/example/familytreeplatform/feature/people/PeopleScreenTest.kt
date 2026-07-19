package com.example.familytreeplatform.feature.people

import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleScreenTest {
    @Test
    fun `directory stats distinguish active and historical profiles`() {
        val stats = familyDirectoryStats(
            listOf(person("1", "ALIVE"), person("2", "DECEASED"), person("3", "UNKNOWN"))
        )

        assertEquals(FamilyDirectoryStats(total = 3, alive = 1, deceased = 1), stats)
    }

    @Test
    fun `person initials remain useful for avatar fallback`() {
        assertEquals("SA", personInitials("Siti Aminah"))
        assertEquals("?", personInitials(" "))
    }

    private fun person(id: String, status: String) = PersonListItem(
        personId = id,
        fullName = "Person $id",
        createdAt = "2026-01-01",
        lifeStatus = status
    )
}
