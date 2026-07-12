package com.example.familytreeplatform.feature.people

import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleUiStateTest {
    private val people = listOf(
        PersonListItem("1", "Budi Santoso", "2026-01-01", "ALIVE", gender = "MALE"),
        PersonListItem("2", "Siti Aminah", "2026-01-02", "ALIVE", gender = "FEMALE")
    )

    @Test
    fun `filter is case insensitive`() {
        val state = PeopleUiState(people = people, query = "sITI")
        assertEquals(listOf("Siti Aminah"), state.filteredPeople.map { it.fullName })
    }

    @Test
    fun `blank query returns all people`() {
        assertEquals(people, PeopleUiState(people = people, query = " ").filteredPeople)
    }
}
