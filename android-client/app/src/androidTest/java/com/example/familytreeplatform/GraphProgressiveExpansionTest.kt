package com.example.familytreeplatform

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.example.familytreeplatform.models.ExportRelationship
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Rule
import org.junit.Test

class GraphProgressiveExpansionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun siblingFamilyOpensAndClosesWithoutChangingTheGraphCenter() {
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
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "older",
                    selectedPersonId = null,
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
                    onSelectPerson = {},
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("Kakak", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Adik", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Nara", substring = true)).assertCountEquals(0)

        composeRule.onNodeWithTag("lineage-children-younger")
            .performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lineage-children-younger")
            .assertContentDescriptionEquals("Tutup cabang anak")
        composeRule.onAllNodes(hasContentDescription("Kakak", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Nara", substring = true)).assertCountEquals(1)

        composeRule.onNodeWithTag("lineage-children-younger")
            .performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("lineage-children-younger")
            .assertContentDescriptionEquals("Buka cabang anak")
        composeRule.onAllNodes(hasContentDescription("Kakak", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Dewi", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Nara", substring = true)).assertCountEquals(0)
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

    private fun spouse(id: String, personId: String, spouseId: String) = ExportRelationship(
        relationshipId = id,
        type = "SPOUSE",
        fromPersonId = personId,
        toPersonId = spouseId,
        meta = "MARRIED",
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
