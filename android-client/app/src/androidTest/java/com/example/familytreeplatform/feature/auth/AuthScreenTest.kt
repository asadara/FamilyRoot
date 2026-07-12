package com.example.familytreeplatform.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import com.example.familytreeplatform.repository.PersonRepository
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signInFormIsAccessible() {
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                AuthScreen(repository = PersonRepository())
            }
        }

        composeRule.onNode(hasText("Sign in") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
    }
}
