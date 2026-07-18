package com.example.familytreeplatform.feature.graph

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class TreeGraphViewModelTest {
    @Test
    fun `initial center prefers connected family member over first disconnected duplicate`() {
        val people = listOf(
            person("duplicate", "Raka duplicate", "2026-07-17T12:00:00Z"),
            person("ayah", "Budi", "2026-07-17T11:00:00Z"),
            person("ibu", "Siti", "2026-07-17T10:00:00Z"),
            person("anak", "Raka", "2026-07-17T09:00:00Z"),
            person("kakek", "Hadi", "2026-07-17T08:00:00Z"),
            person("nenek", "Nur", "2026-07-17T07:00:00Z"),
            person("istri", "Alya", "2026-07-17T06:00:00Z")
        )
        val relationships = listOf(
            relationship("kakek-ayah", "PARENT_CHILD", "kakek", "ayah"),
            relationship("nenek-ibu", "PARENT_CHILD", "nenek", "ibu"),
            relationship("ayah-ibu", "SPOUSE", "ayah", "ibu"),
            relationship("ayah-anak", "PARENT_CHILD", "ayah", "anak"),
            relationship("ibu-anak", "PARENT_CHILD", "ibu", "anak"),
            relationship("anak-istri", "SPOUSE", "anak", "istri")
        )

        assertTrue(chooseInitialCenterPersonId(people, relationships) in setOf("ayah", "ibu"))
    }

    @Test
    fun `initial center falls back to a person when no relationships exist`() {
        val people = listOf(person("only", "Only person", "2026-07-17T12:00:00Z"))

        assertEquals("only", chooseInitialCenterPersonId(people, emptyList()))
    }

    @Test
    fun `graph center selection stays within large-family CI budget`() {
        val people = (0 until 10_000).map { index ->
            person("person-$index", "Person $index", "2026-07-18T00:00:00Z")
        }
        val relationships = (1 until people.size).map { index ->
            relationship(
                "relation-$index",
                "PARENT_CHILD",
                "person-${index - 1}",
                "person-$index"
            )
        }

        var selected: String? = null
        val elapsedMs = measureTimeMillis {
            selected = chooseInitialCenterPersonId(people, relationships)
        }

        assertTrue("Graph selection took ${elapsedMs}ms (budget 1500ms)", elapsedMs <= 1_500)
        assertTrue(selected != null)
    }

    private fun person(id: String, name: String, createdAt: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = createdAt,
        lifeStatus = "ALIVE"
    )

    private fun relationship(id: String, type: String, from: String, to: String) = ExportRelationship(
        relationshipId = id,
        type = type,
        fromPersonId = from,
        toPersonId = to,
        meta = null,
        createdAt = "2026-07-17T12:00:00Z"
    )
}
