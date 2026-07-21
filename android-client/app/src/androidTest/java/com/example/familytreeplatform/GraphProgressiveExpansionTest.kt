package com.example.familytreeplatform

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphProgressiveExpansionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun siblingFamilyOpensAndClosesWithoutChangingTheGraphCenter() {
        var exportedIds = emptySet<String>()
        val relationships = listOf(
            parentChild("father-older", "father", "older"),
            parentChild("mother-older", "mother", "older"),
            parentChild("father-younger", "father", "younger"),
            parentChild("mother-younger", "mother", "younger"),
            spouse("older-spouse", "older", "older-wife"),
            parentChild("older-child-a", "older", "older-child"),
            parentChild("older-child-b", "older-wife", "older-child"),
            spouse("younger-spouse", "younger", "younger-wife"),
            parentChild("younger-child-a", "younger", "younger-child"),
            parentChild("younger-child-b", "younger-wife", "younger-child")
        )
        composeRule.setContent {
            var selectedPersonId by remember { mutableStateOf<String?>(null) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "older",
                    selectedPersonId = selectedPersonId,
                    persons = listOf(
                        person("father", "Ayah"),
                        person("mother", "Ibu"),
                        person("older", "Kakak"),
                        person("older-wife", "Alya"),
                        person("older-child", "Lala"),
                        person("younger", "Adik"),
                        person("younger-wife", "Dewi"),
                        person("younger-child", "Nara")
                    ),
                    relations = RelationsResponse(
                        personId = "older",
                        parents = relationships
                            .filter { it.toPersonId == "older" }
                            .map { it.toRelationItem() },
                        children = relationships
                            .filter { it.fromPersonId == "older" && it.type == "PARENT_CHILD" }
                            .map { it.toRelationItem() },
                        spouses = relationships
                            .filter { it.fromPersonId == "older" && it.type == "SPOUSE" }
                            .map { it.toRelationItem() }
                    ),
                    allRelationships = relationships,
                    onSelectPerson = { selectedPersonId = it },
                    onExportSnapshotChanged = { snapshot ->
                        exportedIds = snapshot.tiles.mapTo(mutableSetOf()) { it.id }
                    },
                    onClearSelection = { selectedPersonId = null },
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("Kakak", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Adik", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Nara", substring = true)).assertCountEquals(0)

        composeRule.onNode(hasContentDescription("Adik", substring = true))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Adik", substring = true)).assertIsSelected()
        composeRule.onNodeWithTag("lineage-children-younger")
            .performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lineage-children-younger")
            .assertContentDescriptionEquals("Tutup cabang anak")
        composeRule.onAllNodes(hasContentDescription("Kakak", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Nara", substring = true)).assertCountEquals(1)
        assertTrue("Export snapshot must follow expanded workspace", "younger-child" in exportedIds)

        composeRule.onNodeWithTag("lineage-children-younger")
            .performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lineage-children-younger")
            .assertContentDescriptionEquals("Buka cabang anak")
        composeRule.onAllNodes(hasContentDescription("Kakak", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Nara", substring = true)).assertCountEquals(0)
        assertFalse("Export snapshot must follow collapsed workspace", "younger-child" in exportedIds)
    }

    @Test
    fun historicalPartnershipKeepsItsOwnChildBranchAndRestoresExpansionState() {
        val relationships = listOf(
            spouse(
                id = "old-partnership",
                personId = "center",
                spouseId = "old-partner",
                meta = "DIVORCED",
                startDate = "2000-01-01",
                endDate = "2008-01-01"
            ),
            spouse(
                id = "current-partnership",
                personId = "center",
                spouseId = "current-partner",
                meta = "MARRIED",
                startDate = "2015-01-01"
            ),
            parentChild("old-child-a", "center", "old-child"),
            parentChild("old-child-b", "old-partner", "old-child"),
            parentChild("current-child-a", "center", "current-child"),
            parentChild("current-child-b", "current-partner", "current-child")
        )
        composeRule.setContent {
            var selectedPersonId by remember { mutableStateOf<String?>(null) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "center",
                    selectedPersonId = selectedPersonId,
                    persons = listOf(
                        person("center", "Bima"),
                        person("old-partner", "Ayu"),
                        person("old-child", "Citra"),
                        person("current-partner", "Dewi"),
                        person("current-child", "Eka")
                    ),
                    relations = RelationsResponse(
                        personId = "center",
                        parents = emptyList(),
                        children = relationships
                            .filter { it.fromPersonId == "center" && it.type == "PARENT_CHILD" }
                            .map { it.toRelationItem() },
                        spouses = relationships
                            .filter { it.type == "SPOUSE" }
                            .map { it.toRelationItem() }
                    ),
                    allRelationships = relationships,
                    onSelectPerson = { selectedPersonId = it },
                    onClearSelection = { selectedPersonId = null },
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("Bima", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Eka", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Ayu", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Citra", substring = true)).assertCountEquals(0)

        composeRule.onNode(hasContentDescription("Bima", substring = true))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Bima", substring = true)).assertIsSelected()
        composeRule.onNodeWithTag("lineage-partnerships-center").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription("Ayu", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Citra", substring = true)).assertCountEquals(0)
        composeRule.onNode(hasContentDescription("Ayu", substring = true))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lineage-children-old-partner").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription("Citra", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Bima", substring = true)).assertCountEquals(1)

        composeRule.onNode(hasContentDescription("Bima", substring = true))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lineage-partnerships-center").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription("Ayu", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Citra", substring = true)).assertCountEquals(0)

        composeRule.onNode(hasContentDescription("Bima", substring = true))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lineage-partnerships-center").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription("Ayu", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Citra", substring = true)).assertCountEquals(1)
        assertPersonCardsDoNotOverlap("Bima", "Ayu", "Citra", "Dewi", "Eka")
    }

    private fun assertPersonCardsDoNotOverlap(vararg names: String) {
        val cards = names.map { name ->
            name to composeRule
                .onAllNodes(hasContentDescription(name, substring = true))
                .fetchSemanticsNodes()
                .single()
                .boundsInRoot
        }
        cards.forEachIndexed { index, (firstName, firstBounds) ->
            cards.drop(index + 1).forEach { (secondName, secondBounds) ->
                assertFalse(
                    "$firstName overlaps $secondName",
                    firstBounds.overlaps(secondBounds)
                )
            }
        }
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-07-19",
        lifeStatus = "ALIVE",
        birthDate = "1990-01-01"
    )

    private fun parentChild(id: String, parentId: String, childId: String) = ExportRelationship(
        relationshipId = id,
        type = "PARENT_CHILD",
        fromPersonId = parentId,
        toPersonId = childId,
        meta = "BIOLOGICAL",
        createdAt = "2026-07-19"
    )

    private fun spouse(
        id: String,
        personId: String,
        spouseId: String,
        meta: String = "MARRIED",
        startDate: String? = null,
        endDate: String? = null
    ) = ExportRelationship(
        relationshipId = id,
        type = "SPOUSE",
        fromPersonId = personId,
        toPersonId = spouseId,
        meta = meta,
        startDate = startDate,
        endDate = endDate,
        createdAt = "2026-07-19"
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
