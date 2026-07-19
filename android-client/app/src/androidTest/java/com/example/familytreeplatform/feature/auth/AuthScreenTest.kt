package com.example.familytreeplatform.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
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
                AuthScreen(viewModel = AuthViewModel(PersonRepository()))
            }
        }

        composeRule.onNodeWithTag("authSubmit").assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Kata sandi").assertIsDisplayed()
        composeRule.onNodeWithText("Buat akun").assertIsDisplayed()
    }
}
