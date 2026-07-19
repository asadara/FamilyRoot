package com.example.familytreeplatform

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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

        composeRule.onNodeWithTag("graph-workspace").performTouchInput { swipeLeft(durationMillis = 600) }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithText("Budi").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Expected graph to move left, before=$before after=$after", after < before - 20f)
    }
}
