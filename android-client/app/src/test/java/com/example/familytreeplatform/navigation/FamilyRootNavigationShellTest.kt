package com.example.familytreeplatform.navigation

import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyRootNavigationShellTest {
    private val people = listOf(
        person("1", "Budi Santoso"),
        person("2", "Siti Aminah"),
        person("3", "Raka Budi")
    )

    @Test
    fun `search is case insensitive and trims query`() {
        assertEquals(
            listOf("Budi Santoso", "Raka Budi"),
            filterShellPeople("  bUdI ", people).map { it.fullName }
        )
    }

    @Test
    fun `blank search has no result and limit is respected`() {
        assertEquals(emptyList<PersonListItem>(), filterShellPeople(" ", people))
        assertEquals(1, filterShellPeople("i", people, limit = 1).size)
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = "ALIVE"
    )
}
