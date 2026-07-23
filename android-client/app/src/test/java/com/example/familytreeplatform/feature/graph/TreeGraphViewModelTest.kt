package com.example.familytreeplatform.feature.graph

import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.familyGenerationLevels
import com.example.familytreeplatform.unconnectedPersonIds
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
    fun `first selection reveals card controls without opening inspector`() {
        val initial = TreeGraphUiState(
            centerPersonId = "self",
            selectedPersonId = "self"
        )

        val selected = selectGraphPerson(initial, "relative")

        assertEquals("self", selected.centerPersonId)
        assertEquals("relative", selected.selectedPersonId)
        assertEquals(null, selected.inspectedPersonId)
        assertEquals(null, clearGraphSelection(selected).selectedPersonId)
        assertEquals("self", clearGraphSelection(selected).centerPersonId)
    }

    @Test
    fun `person cache emission reveals first card after empty relationship cache arrived`() {
        val initial = TreeGraphUiState(
            centerPersonId = null,
            persons = emptyList(),
            relationships = emptyList()
        )

        val updated = updateGraphPersons(
            initial,
            listOf(person("first", "Person pertama", "2026-07-22T00:00:00Z"))
        )

        assertEquals("first", updated.centerPersonId)
        assertEquals("first", updated.relations?.personId)
        assertEquals(listOf("first"), updated.explorationHistory)
    }

    @Test
    fun `second activation opens inspector without changing graph center`() {
        val initial = TreeGraphUiState(centerPersonId = "self", selectedPersonId = "relative")

        val inspected = inspectGraphPerson(initial, "relative")

        assertEquals("self", inspected.centerPersonId)
        assertEquals("relative", inspected.selectedPersonId)
        assertEquals("relative", inspected.inspectedPersonId)
        assertEquals(null, clearGraphSelection(inspected).inspectedPersonId)
    }

    @Test
    fun `focus action rebuilds graph around selected person`() {
        val people = listOf(
            person("aji", "Aji", "2026-01-01"),
            person("anisa", "Anisa", "2026-01-01")
        )
        val spouse = relationship("spouse", "SPOUSE", "aji", "anisa")
        val state = TreeGraphUiState(
            centerPersonId = "aji",
            persons = people,
            relationships = listOf(spouse),
            explorationHistory = listOf("aji")
        )

        val focused = focusGraphPerson(state, "anisa")

        assertEquals("anisa", focused.centerPersonId)
        assertEquals("anisa", focused.relations?.personId)
        assertEquals(listOf("spouse"), focused.relations?.spouses?.map { it.relationshipId })
        assertEquals(null, focused.inspectedPersonId)
        assertEquals(listOf("aji", "anisa"), focused.explorationHistory)
        assertEquals(
            listOf("aji", "anisa"),
            focusGraphPerson(focused, "anisa").explorationHistory
        )
    }

    @Test
    fun `new person without a relationship remains discoverable in graph`() {
        val people = listOf(
            person("parent", "Parent", "2026-01-01"),
            person("child", "Child", "2026-01-02"),
            person("new", "Person baru", "2026-01-03")
        )
        val relationships = listOf(
            relationship("parent-child", "PARENT_CHILD", "parent", "child")
        )

        val unconnected = unconnectedPersonIds(people, relationships, visibleIds = setOf("parent"))

        assertEquals(setOf("new"), unconnected)
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
    fun `room relationship update adds a new child without resetting graph state`() {
        val initial = TreeGraphUiState(
            centerPersonId = "budi",
            selectedPersonId = "budi",
            persons = listOf(
                person("budi", "Budi Santoso", "2026-01-01"),
                person("daughter", "Putri", "2026-07-20")
            ),
            explorationHistory = listOf("budi"),
            relationships = emptyList()
        )
        val parentChild = relationship(
            "budi-daughter",
            "PARENT_CHILD",
            "budi",
            "daughter"
        )

        val updated = updateGraphRelationships(initial, listOf(parentChild))

        assertEquals(listOf(parentChild), updated.relationships)
        assertEquals(listOf("daughter"), updated.relations?.children?.map { it.toPersonId })
        assertEquals("budi", updated.centerPersonId)
        assertEquals("budi", updated.selectedPersonId)
        assertEquals(listOf("budi"), updated.explorationHistory)
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
    fun `generation grouping places siblings and spouses with the center`() {
        val relationships = listOf(
            relationship("parent-center", "PARENT_CHILD", "parent", "center"),
            relationship("parent-sibling", "PARENT_CHILD", "parent", "sibling"),
            relationship("center-child", "PARENT_CHILD", "center", "child"),
            relationship("center-spouse", "SPOUSE", "center", "spouse")
        )

        val levels = familyGenerationLevels("center", relationships)

        assertEquals(-1, levels["parent"])
        assertEquals(0, levels["sibling"])
        assertEquals(0, levels["spouse"])
        assertEquals(1, levels["child"])
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
        assertEquals("child", result.inspectedPersonId)
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
    fun `integrity audit recommends removing the newest contradictory relationship`() {
        val people = listOf(
            person("agustya", "Agustya", "2026-01-01"),
            person("nur", "Nur", "2026-01-01")
        )
        val spouse = relationship("spouse", "SPOUSE", "agustya", "nur")
            .copy(createdAt = "2026-07-21T06:57:29Z")
        val accidentalParent = relationship("parent", "PARENT_CHILD", "agustya", "nur")
            .copy(createdAt = "2026-07-21T11:54:20Z", meta = "BIOLOGICAL")

        val conflict = detectRelationshipIntegrityConflicts(
            people,
            listOf(spouse, accidentalParent)
        ).single()

        assertEquals("parent", conflict.recommendedRelationshipId)
        assertTrue(conflict.title.contains("Agustya"))
        assertTrue(conflict.recommendation.contains("ditambahkan paling akhir"))
    }

    @Test
    fun `relationship validation blocks spouse parent overlap and ancestry loops`() {
        val relationships = listOf(
            relationship("grandparent-parent", "PARENT_CHILD", "grandparent", "parent"),
            relationship("parent-child", "PARENT_CHILD", "parent", "child"),
            relationship("partner", "SPOUSE", "a", "b")
        )

        assertTrue(
            validateProposedRelationship(
                "a",
                "b",
                ExistingRelationKind.TARGET_CHILD,
                "BIOLOGICAL",
                relationships
            )?.contains("pasangan") == true
        )
        assertTrue(
            validateProposedRelationship(
                "grandparent",
                "child",
                ExistingRelationKind.PARTNER,
                "MARRIED",
                relationships
            )?.contains("jalur leluhur") == true
        )
        assertTrue(
            validateProposedRelationship(
                "child",
                "grandparent",
                ExistingRelationKind.TARGET_CHILD,
                "BIOLOGICAL",
                relationships
            )?.contains("lingkaran") == true
        )
    }

    @Test
    fun `integrity audit reports more than two biological parents`() {
        val people = listOf(
            person("child", "Anak", "2026-01-01"),
            person("p1", "P1", "2026-01-01"),
            person("p2", "P2", "2026-01-01"),
            person("p3", "P3", "2026-01-01")
        )
        val relationships = listOf(
            relationship("r1", "PARENT_CHILD", "p1", "child")
                .copy(meta = "BIOLOGICAL", createdAt = "2026-01-01"),
            relationship("r2", "PARENT_CHILD", "p2", "child")
                .copy(meta = "BIOLOGICAL", createdAt = "2026-01-02"),
            relationship("r3", "PARENT_CHILD", "p3", "child")
                .copy(meta = "BIOLOGICAL", createdAt = "2026-01-03")
        )

        val conflict = detectRelationshipIntegrityConflicts(people, relationships).single()

        assertEquals("r3", conflict.recommendedRelationshipId)
        assertTrue(conflict.title.contains("lebih dari dua"))
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
