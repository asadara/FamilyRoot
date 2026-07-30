package com.example.familytreeplatform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GraphPanGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneFingerSwipeMovesGraphWithoutSelectingPerson() {
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "center",
                    selectedPersonId = null,
                    persons = listOf(
                        PersonListItem(
                            personId = "center",
                            fullName = "Budi",
                            createdAt = "2026-01-01",
                            lifeStatus = "ALIVE",
                            birthDate = "1985-01-01",
                            gender = "MALE"
                        )
                    ),
                    relations = RelationsResponse(personId = "center"),
                    allRelationships = emptyList(),
                    onSelectPerson = { error("A pan must not select a person") },
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithText("Budi").fetchSemanticsNode().boundsInRoot.left

        composeRule.onNodeWithTag("graph-workspace").performTouchInput {
            swipe(
                start = Offset(center.x + 60f, center.y),
                end = Offset(center.x - 60f, center.y),
                durationMillis = 600
            )
        }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithText("Budi").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected graph to move left, before=$before after=$after", after < before - 20f)
    }

    @Test
    fun focusingFromInspectorKeepsPanZoomAndMinimapEnabled() {
        val people = buildList {
            add(person("origin", "Asal"))
            add(person("focus", "Fokus"))
            repeat(20) { index -> add(person("loose-$index", "Lepas $index")) }
        }
        composeRule.setContent {
            var centerId by remember { mutableStateOf("origin") }
            var selectedId by remember { mutableStateOf<String?>("focus") }
            var inspectedId by remember { mutableStateOf<String?>("focus") }
            var minimapNavigationCount by remember { mutableStateOf(0) }
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = centerId,
                    selectedPersonId = selectedId,
                    inspectedPersonId = inspectedId,
                    persons = people,
                    relations = RelationsResponse(personId = centerId),
                    allRelationships = emptyList(),
                    onSelectPerson = {
                        selectedId = it
                        inspectedId = null
                    },
                    onInspectPerson = {
                        selectedId = it
                        inspectedId = it
                    },
                    onFocusPerson = {
                        centerId = it
                        selectedId = null
                        inspectedId = null
                    },
                    onClearSelection = {
                        selectedId = null
                        inspectedId = null
                    },
                    onMinimapNavigation = { minimapNavigationCount++ },
                    onOpenPerson = {},
                    onBack = {}
                )
                androidx.compose.material3.Text("Minimap navigations: $minimapNavigationCount")
            }
        }

        composeRule.onNodeWithText("Jadikan Fokus pusat pohon")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        val beforeBounds = composeRule.onNodeWithText("Fokus").fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("graph-workspace")
            .performTouchInput {
                swipe(
                    start = Offset(center.x + 60f, center.y),
                    end = Offset(center.x - 60f, center.y),
                    durationMillis = 600
                )
            }
        composeRule.waitForIdle()

        val afterPanBounds = composeRule.onNodeWithText("Fokus").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected focused graph to remain pannable, before=${beforeBounds.left} after=${afterPanBounds.left}",
            afterPanBounds.left < beforeBounds.left - 20f
        )

        composeRule.onNodeWithTag("graph-zoom-in").performClick()
        composeRule.waitForIdle()
        val afterZoomBounds = composeRule.onNodeWithText("Fokus").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected focused graph to remain zoomable, before=${afterPanBounds.width} after=${afterZoomBounds.width}",
            afterZoomBounds.width > afterPanBounds.width
        )

        composeRule.onNodeWithTag("graph-minimap")
            .assertIsDisplayed()
            .performTouchInput {
                click(Offset(12f, 72f))
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Minimap navigations: 1").assertIsDisplayed()
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = "ALIVE"
    )
}
