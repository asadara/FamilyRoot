package com.example.familytreeplatform

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainActivityRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun authenticationRouteSurvivesActivityRecreation() {
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Sign in") and hasClickAction()).assertIsDisplayed()
    }
}
