package com.example.familytreeplatform

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.feature.graph.ExistingRelationKind
import com.example.familytreeplatform.feature.graph.validateProposedRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareRelationshipSemanticsTest {
    @Test
    fun `foster and guardian never alter generation levels`() {
        val relationships = listOf(
            relationship("foster", "caregiver", "child", "FOSTER"),
            relationship("guardian", "guardian", "child", "GUARDIAN")
        )

        val levels = familyGenerationLevels("child", relationships)

        assertEquals(0, levels["child"])
        assertFalse("caregiver" in levels)
        assertFalse("guardian" in levels)
        assertTrue(recordedParentPersonIds("child", relationships).isEmpty())
    }

    @Test
    fun `care-only people remain independently placeable in workspace`() {
        val people = listOf(
            person("caregiver", "Ibu Asuh"),
            person("child", "Budi")
        )
        val relationships = listOf(
            relationship("foster", "caregiver", "child", "FOSTER")
        )

        assertEquals(
            setOf("caregiver", "child"),
            unconnectedPersonIds(people, relationships)
        )
    }

    @Test
    fun `care metadata has distinct semantics`() {
        val foster = relationship("foster", "caregiver", "child", "FOSTER")
        val guardian = relationship("guardian", "guardian", "child", "GUARDIAN")
        val biological = relationship("bio", "parent", "child", "BIOLOGICAL")

        assertTrue(foster.isCareRelationship())
        assertTrue(guardian.isCareRelationship())
        assertFalse(foster.isLineageParentChild())
        assertTrue(biological.isLineageParentChild())
    }

    @Test
    fun `care relationship does not create an ancestry cycle`() {
        val relationships = listOf(
            relationship("parent-child", "parent", "child", "BIOLOGICAL"),
            relationship("foster", "child", "grandparent", "FOSTER")
        )

        assertEquals(
            null,
            validateProposedRelationship(
                sourcePersonId = "grandparent",
                targetPersonId = "parent",
                kind = ExistingRelationKind.TARGET_CHILD,
                meta = "GUARDIAN",
                relationships = relationships
            )
        )
    }

    private fun relationship(
        id: String,
        from: String,
        to: String,
        meta: String
    ) = ExportRelationship(
        relationshipId = id,
        type = "PARENT_CHILD",
        fromPersonId = from,
        toPersonId = to,
        meta = meta,
        createdAt = "2026-07-28T00:00:00Z"
    )

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        gender = "UNKNOWN",
        birthDate = null,
        lifeStatus = "UNKNOWN",
        createdAt = "2026-07-28T00:00:00Z"
    )
}
