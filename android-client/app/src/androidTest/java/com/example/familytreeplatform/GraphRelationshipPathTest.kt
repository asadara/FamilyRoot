package com.example.familytreeplatform

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.models.RelationshipPathEdge
import com.example.familytreeplatform.models.RelationshipPathPerson
import com.example.familytreeplatform.models.RelationshipPathResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GraphRelationshipPathTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun relationshipPathIsTextFirstAndGraphHighlightRequiresExplicitAction() {
        var showPathRequested = false
        var hideBreadcrumbRequested = false
        val relationship = ExportRelationship(
            relationshipId = "parent-child",
            type = "PARENT_CHILD",
            fromPersonId = "parent",
            toPersonId = "child",
            meta = "BIOLOGICAL",
            createdAt = "2026-01-01"
        )
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "parent",
                    selectedPersonId = "child",
                    persons = listOf(person("parent", "Hadi"), person("child", "Budi")),
                    relations = RelationsResponse(
                        personId = "parent",
                        children = listOf(relationship.toRelationItem())
                    ),
                    allRelationships = listOf(relationship),
                    explorationHistory = listOf("parent", "child"),
                    relationshipPath = RelationshipPathResponse(
                        found = true,
                        people = listOf(
                            RelationshipPathPerson("parent", "Hadi"),
                            RelationshipPathPerson("child", "Budi")
                        ),
                        edges = listOf(
                            RelationshipPathEdge(
                                relationshipId = "parent-child",
                                type = "PARENT_CHILD",
                                fromPersonId = "parent",
                                toPersonId = "child",
                                meta = "BIOLOGICAL",
                                direction = "FORWARD"
                            )
                        )
                    ),
                    showRelationshipPathInGraph = false,
                    onSelectPerson = {},
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {},
                    onShowRelationshipPath = { showPathRequested = true },
                    onHideExplorationBreadcrumb = { hideBreadcrumbRequested = true }
                )
            }
        }

        composeRule.onNodeWithText("Jalur hubungan terpendek").assertIsDisplayed()
        composeRule.onNodeWithText("orang tua dari Budi", substring = true).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!showPathRequested) }
        composeRule.onNodeWithContentDescription("Sembunyikan jejak").performClick()
        composeRule.runOnIdle { assertTrue(hideBreadcrumbRequested) }
        composeRule.onNodeWithText("Tampilkan jalur di pohon").performClick()
        composeRule.runOnIdle { assertTrue(showPathRequested) }
    }

    @Test
    fun explicitPathAddsAncestorOutsideInitiallyRenderedGeneration() {
        val showPath = mutableStateOf(false)
        val parent = parentChild("parent-child", "parent", "center")
        val grandparent = parentChild("grandparent-parent", "grandparent", "parent")
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "center",
                    selectedPersonId = "grandparent",
                    persons = listOf(
                        person("center", "Budi"),
                        person("parent", "Hadi"),
                        person("grandparent", "Mbah")
                    ),
                    relations = RelationsResponse(
                        personId = "center",
                        parents = listOf(parent.toRelationItem())
                    ),
                    allRelationships = listOf(parent, grandparent),
                    explorationHistory = listOf("center", "grandparent"),
                    relationshipPath = RelationshipPathResponse(
                        found = true,
                        people = listOf(
                            RelationshipPathPerson("center", "Budi"),
                            RelationshipPathPerson("parent", "Hadi"),
                            RelationshipPathPerson("grandparent", "Mbah")
                        ),
                        edges = listOf(
                            parent.toPathEdge(direction = "REVERSE"),
                            grandparent.toPathEdge(direction = "REVERSE")
                        )
                    ),
                    showRelationshipPathInGraph = showPath.value,
                    onSelectPerson = {},
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {},
                    onShowRelationshipPath = { showPath.value = true }
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("Mbah", substring = true)).assertCountEquals(0)
        composeRule.onNodeWithText("Tampilkan jalur di pohon").performClick()
        composeRule.onNode(hasContentDescription("Mbah", substring = true)).assertIsDisplayed()
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = "ALIVE",
        birthDate = "1980-01-01"
    )

    private fun parentChild(id: String, parentId: String, childId: String) = ExportRelationship(
        relationshipId = id,
        type = "PARENT_CHILD",
        fromPersonId = parentId,
        toPersonId = childId,
        meta = "BIOLOGICAL",
        createdAt = "2026-01-01"
    )

    private fun ExportRelationship.toPathEdge(direction: String) = RelationshipPathEdge(
        relationshipId = relationshipId,
        type = type,
        fromPersonId = fromPersonId,
        toPersonId = toPersonId,
        meta = meta,
        direction = direction
    )

    private fun ExportRelationship.toRelationItem() = RelationItem(
        relationshipId = relationshipId,
        type = type,
        fromPersonId = fromPersonId,
        toPersonId = toPersonId,
        meta = meta,
        createdAt = createdAt
    )
}
