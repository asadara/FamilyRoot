package com.example.familytreeplatform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import com.example.familytreeplatform.feature.graph.QuickRelationKind
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GraphCardInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionControlsInspectorAndBackgroundFollowLockedGestureStates() {
        var requestedKind: QuickRelationKind? = null
        var clearCount = 0
        composeRule.setContent {
            var selectedPersonId by remember { mutableStateOf<String?>(null) }
            var inspectedPersonId by remember { mutableStateOf<String?>(null) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "budi",
                    selectedPersonId = selectedPersonId,
                    inspectedPersonId = inspectedPersonId,
                    persons = listOf(person("budi", "Budi Santoso")),
                    relations = RelationsResponse(personId = "budi"),
                    allRelationships = emptyList(),
                    onSelectPerson = {
                        selectedPersonId = it
                        inspectedPersonId = null
                    },
                    onInspectPerson = {
                        selectedPersonId = it
                        inspectedPersonId = it
                    },
                    onQuickAddRequest = { requestedKind = it.kind },
                    onClearSelection = {
                        clearCount++
                        selectedPersonId = null
                        inspectedPersonId = null
                    },
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onAllNodes(hasContentDescription("Tambah anak"))
            .assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Budi Santoso")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Budi Santoso").assertIsSelected()
        composeRule.onAllNodes(hasContentDescription("Tambah orang tua")).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Tambah anak")).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Tambah pasangan")).assertCountEquals(1)
        composeRule.onAllNodes(hasText("Lihat profil lengkap"))
            .assertCountEquals(0)

        composeRule.onNodeWithTag("quick-add-child-budi")
            .performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(QuickRelationKind.CHILD, requestedKind) }

        composeRule.onNodeWithContentDescription("Budi Santoso")
            .performClick()
        composeRule.onNodeWithText("Lihat profil lengkap").assertIsDisplayed()

        composeRule.onNodeWithTag("graph-background", useUnmergedTree = true)
            .performTouchInput { click(Offset(4f, 4f)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, clearCount) }
        composeRule.onAllNodes(hasContentDescription("Tambah anak"))
            .assertCountEquals(0)
        composeRule.onAllNodes(hasText("Lihat profil lengkap"))
            .assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Budi Santoso")
            .performTouchInput { doubleClick(center) }
        composeRule.onNodeWithText("Lihat profil lengkap").assertIsDisplayed()
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-07-20",
        lifeStatus = "ALIVE"
    )
}
