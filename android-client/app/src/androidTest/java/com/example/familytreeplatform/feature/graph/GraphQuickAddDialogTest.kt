package com.example.familytreeplatform.feature.graph

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Rule
import org.junit.Test

class GraphQuickAddDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun partnerDateClearlyMeansRelationshipStartAndUsesPicker() {
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphQuickAddDialog(
                    request = GraphQuickAddRequest(
                        anchorPersonId = "raka",
                        anchorName = "Raka",
                        kind = QuickRelationKind.PARTNER
                    ),
                    saving = false,
                    error = null,
                    onDismiss = {},
                    onOpenProfile = {},
                    onSave = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("relationship-start-date").assertIsDisplayed()
        composeRule.onNodeWithText("Tanggal mulai hubungan").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Bukan tanggal lahir. Tanggal lahir dapat dilengkapi di profil."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Pilih").assertIsDisplayed()
    }

    @Test
    fun parentQuickAddDoesNotAskForRelationshipStartDate() {
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                GraphQuickAddDialog(
                    request = GraphQuickAddRequest(
                        anchorPersonId = "raka",
                        anchorName = "Raka",
                        kind = QuickRelationKind.PARENT
                    ),
                    saving = false,
                    error = null,
                    onDismiss = {},
                    onOpenProfile = {},
                    onSave = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onAllNodesWithTag("relationship-start-date").assertCountEquals(0)
    }
}
