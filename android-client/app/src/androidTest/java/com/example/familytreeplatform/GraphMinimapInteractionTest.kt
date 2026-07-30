package com.example.familytreeplatform

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.models.RelationsResponse
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Rule
import org.junit.Test

class GraphMinimapInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun minimapCanBeClosedAndShownAgainFromAnExternalRequest() {
        val showRequest = mutableIntStateOf(0)
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphScreen(
                    centerPersonId = "center",
                    selectedPersonId = null,
                    persons = buildList {
                        add(person("center", "Person utama"))
                        repeat(28) { index ->
                            add(person("unconnected-$index", "Person lepas $index"))
                        }
                    },
                    relations = RelationsResponse(personId = "center"),
                    allRelationships = emptyList(),
                    showMinimapRequest = showRequest.intValue,
                    onSelectPerson = {},
                    onClearSelection = {},
                    onOpenPerson = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("graph-minimap").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-minimap-close").performClick()
        composeRule.onAllNodesWithTag("graph-minimap").assertCountEquals(0)

        composeRule.runOnUiThread { showRequest.intValue++ }
        composeRule.onNodeWithTag("graph-minimap").assertIsDisplayed()
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = "ALIVE"
    )
}
