package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphRelationshipLayoutTest {
    private val people = listOf(
        person("older", "Kakak", "1990-01-01"),
        person("center", "Tengah", "1995-01-01"),
        person("younger", "Adik", "2000-01-01")
    )

    @Test
    fun `siblings are derived from a shared parent and ordered by birth`() {
        val relationships = listOf(
            parentChild("parent-a", "center"),
            parentChild("parent-a", "younger"),
            parentChild("parent-a", "older"),
            parentChild("other-parent", "unrelated")
        )

        assertEquals(
            listOf("older", "younger"),
            findSiblingIds("center", relationships, people)
        )
    }

    @Test
    fun `person without recorded parent has no sibling controls`() {
        assertEquals(emptyList<String>(), findSiblingIds("center", emptyList(), people))
    }

    private fun person(id: String, name: String, birthDate: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = "ALIVE",
        birthDate = birthDate
    )

    private fun parentChild(parentId: String, childId: String) = ExportRelationship(
        relationshipId = "$parentId-$childId",
        type = "PARENT_CHILD",
        fromPersonId = parentId,
        toPersonId = childId,
        meta = "BIOLOGICAL",
        createdAt = "2026-01-01"
    )
}
