package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GraphRelationshipLayoutTest {
    @Test
    fun `partnership swap reverses only the selected pair`() {
        val selectedPair = partnershipPairKey("budi", "siti")

        assertEquals(
            "budi" to "siti",
            orientedPartnershipPair("budi", "siti", emptySet())
        )
        assertEquals(
            "siti" to "budi",
            orientedPartnershipPair("budi", "siti", setOf(selectedPair))
        )
        assertEquals(
            "aji" to "anisa",
            orientedPartnershipPair("aji", "anisa", setOf(selectedPair))
        )
    }

    @Test
    fun `workspace card prioritizes family nickname over first word`() {
        assertEquals(
            "Bude Ani",
            cardDisplayName(person("ani", "Anindita Kusuma", "1980-01-01").copy(nickName = "Bude Ani"))
        )
        assertEquals(
            "Anindita",
            cardDisplayName(person("ani", "Anindita Kusuma", "1980-01-01"))
        )
        assertEquals(
            "Nn",
            cardDisplayName(person("nn", "Nn", "1980-01-01").copy(nickName = "-"))
        )
    }

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

    @Test
    fun `siblings without birth dates use stable record order without claiming an age order`() {
        val undatedPeople = listOf(
            person("first-recorded", "Pertama", "1990-01-01").copy(
                birthDate = null,
                createdAt = "2026-07-20T08:00:00Z"
            ),
            person("later-recorded", "Berikutnya", "1990-01-01").copy(
                birthDate = null,
                createdAt = "2026-07-20T09:00:00Z"
            ),
            person("center", "Tengah", "1995-01-01")
        )
        val relationships = listOf(
            parentChild("parent", "center"),
            parentChild("parent", "later-recorded"),
            parentChild("parent", "first-recorded")
        )

        assertEquals(
            listOf("first-recorded", "later-recorded"),
            findSiblingIds("center", relationships, undatedPeople)
        )
    }

    @Test
    fun `long press position becomes the center of an unconnected card`() {
        val placement = nearestAvailableGraphCardRect(
            preferred = GraphPreferredPosition(centerX = 420f, centerY = 300f),
            occupied = emptyList(),
            tileWidth = 96f,
            tileHeight = 108f,
            horizontalStep = 124f,
            verticalStep = 152f,
            margin = 80f
        )

        assertEquals(420f, placement.centerX, 0.01f)
        assertEquals(300f, placement.y + placement.height / 2f, 0.01f)
    }

    @Test
    fun `long press position moves to nearest free slot when a card occupies it`() {
        val occupied = LineagePlacementRect(372f, 246f, 96f, 108f)
        val placement = nearestAvailableGraphCardRect(
            preferred = GraphPreferredPosition(centerX = 420f, centerY = 300f),
            occupied = listOf(occupied),
            tileWidth = 96f,
            tileHeight = 108f,
            horizontalStep = 124f,
            verticalStep = 152f,
            margin = 80f
        )

        assertFalse(occupied.overlaps(placement, padding = 8f))
        assertEquals(420f, placement.centerX, 124.01f)
        assertEquals(300f, placement.y + placement.height / 2f, 152.01f)
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
