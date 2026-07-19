package com.example.familytreeplatform.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.familytreeplatform.models.PersonListItem
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationShellSearchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactSearchReturnsPersonAndInvokesSelection() {
        var selectedPersonId: String? = null
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                FamilyRootNavigationShell(
                    currentRoute = Routes.GRAPH,
                    onNavigate = {},
                    spaceName = "Keluarga Demo",
                    userDisplayName = "Budi Santoso",
                    userEmail = "father@family.test",
                    people = listOf(person("raka", "Raka Santoso"), person("siti", "Siti Aminah")),
                    pendingSyncCount = 0,
                    onSearchPerson = { selectedPersonId = it },
                    onOpenSettings = {},
                    onSignOut = {}
                ) { contentModifier ->
                    Box(modifier = contentModifier.fillMaxSize())
                }
            }
        }

        val compactSearchButtons = composeRule.onAllNodesWithText("Cari").fetchSemanticsNodes()
        if (compactSearchButtons.isNotEmpty()) {
            composeRule.onNodeWithText("Cari").performClick()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Raka")
        composeRule.onNodeWithText("Raka Santoso").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals("raka", selectedPersonId) }
    }

    @Test
    fun accountAvatarShowsSignedInIdentityBeforeOpeningSpaceSettings() {
        var profileOpened = false
        var settingsOpened = false
        var signedOut = false
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                FamilyRootNavigationShell(
                    currentRoute = Routes.GRAPH,
                    onNavigate = {},
                    spaceName = "Keluarga Demo",
                    userDisplayName = "Budi Santoso",
                    userEmail = "father@family.test",
                    people = emptyList(),
                    pendingSyncCount = 0,
                    onSearchPerson = {},
                    onOpenProfile = { profileOpened = true },
                    onOpenSettings = { settingsOpened = true },
                    onSignOut = { signedOut = true }
                ) { contentModifier ->
                    Box(modifier = contentModifier.fillMaxSize())
                }
            }
        }

        composeRule.onNodeWithContentDescription("Akun Budi Santoso").performClick()
        composeRule.onNodeWithText("father@family.test").assertIsDisplayed()
        composeRule.onNodeWithText("Akun yang sedang digunakan").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(settingsOpened)
            assertFalse(signedOut)
            assertFalse(profileOpened)
        }

        composeRule.onNodeWithText("Lihat profil akun").performClick()
        composeRule.runOnIdle { assertTrue(profileOpened) }

        composeRule.onNodeWithContentDescription("Akun Budi Santoso").performClick()
        composeRule.onNodeWithText("Pengaturan Family Space").performClick()
        composeRule.runOnIdle {
            assertTrue(settingsOpened)
            assertFalse(signedOut)
        }
    }

    private fun person(id: String, name: String) = PersonListItem(
        personId = id,
        fullName = name,
        createdAt = "2026-01-01",
        lifeStatus = "ALIVE"
    )
}
