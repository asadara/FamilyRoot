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
    fun `selecting a person opens inspector state without changing graph center`() {
        val initial = TreeGraphUiState(
            centerPersonId = "self",
            selectedPersonId = "self"
        )

        val selected = selectGraphPerson(initial, "relative")

        assertEquals("self", selected.centerPersonId)
        assertEquals("relative", selected.selectedPersonId)
        assertEquals(null, clearGraphSelection(selected).selectedPersonId)
        assertEquals("self", clearGraphSelection(selected).centerPersonId)
    }

    @Test
    fun `room profile update replaces stale workspace person without resetting graph state`() {
        val stalePerson = person("self", "Budi", "2026-01-01").copy(
            notes = "Demo seed profile",
            version = 1
        )
        val syncedPerson = stalePerson.copy(notes = "Uji offline Step 7", version = 2)
        val initial = TreeGraphUiState(
            centerPersonId = "self",
            selectedPersonId = "self",
            persons = listOf(stalePerson),
            explorationHistory = listOf("self"),
            explorationBreadcrumbVisible = true
        )

        val updated = updateGraphPersons(initial, listOf(syncedPerson))

        assertEquals("Uji offline Step 7", updated.persons.single().notes)
        assertEquals(2, updated.persons.single().version)
        assertEquals("self", updated.centerPersonId)
        assertEquals("self", updated.selectedPersonId)
        assertEquals(listOf("self"), updated.explorationHistory)
        assertTrue(updated.explorationBreadcrumbVisible)
    }

    @Test
    fun `shortest relationship path is directional and works from cached graph data`() {
        val people = listOf(
            person("parent", "Hadi", "2026-01-01"),
            person("child", "Budi", "2026-01-01"),
            person("spouse", "Siti", "2026-01-01")
        )
        val relationships = listOf(
            relationship("parent-child", "PARENT_CHILD", "parent", "child"),
            relationship("child-spouse", "SPOUSE", "child", "spouse")
        )

        val forward = findShortestRelationshipPath("parent", "spouse", people, relationships)
        val reverse = findShortestRelationshipPath("spouse", "parent", people, relationships)

        assertTrue(forward.found)
        assertEquals(listOf("parent", "child", "spouse"), forward.people.map { it.personId })
        assertEquals(listOf("FORWARD", "FORWARD"), forward.edges.map { it.direction })
        assertEquals(listOf("REVERSE", "REVERSE"), reverse.edges.map { it.direction })
    }

    @Test
    fun `search result advances exploration focus without showing graph path automatically`() {
        val state = TreeGraphUiState(
            centerPersonId = "parent",
            persons = listOf(
                person("parent", "Hadi", "2026-01-01"),
                person("child", "Budi", "2026-01-01")
            ),
            relationships = listOf(
                relationship("parent-child", "PARENT_CHILD", "parent", "child")
            ),
            explorationHistory = listOf("parent")
        )

        val result = selectGraphSearchResult(state, "child")

        assertEquals("parent", result.centerPersonId)
        assertEquals("child", result.selectedPersonId)
        assertEquals(listOf("parent", "child"), result.explorationHistory)
        assertTrue(result.explorationBreadcrumbVisible)
        assertTrue(result.relationshipPath?.found == true)
        assertEquals(false, result.showRelationshipPathInGraph)
    }

    @Test
    fun `disconnected people return a not found path`() {
        val people = listOf(
            person("a", "A", "2026-01-01"),
            person("b", "B", "2026-01-01")
        )

        assertEquals(false, findShortestRelationshipPath("a", "b", people, emptyList()).found)
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
