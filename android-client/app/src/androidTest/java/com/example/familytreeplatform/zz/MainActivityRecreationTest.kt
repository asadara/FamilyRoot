package com.example.familytreeplatform.zz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.familytreeplatform.MainActivity
import com.example.familytreeplatform.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class MainActivityRecreationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val clearSessionRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                runBlocking {
                    withTimeout(15_000) {
                        SessionStore.restoring.filter { restoring -> !restoring }.first()
                    }
                }
                SessionStore.clear()
                base.evaluate()
            }
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(clearSessionRule)
        .around(composeRule)

    @Test
    fun authenticationRouteSurvivesActivityRecreation() {
        composeRule.onNodeWithTag("authSubmit").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("authSubmit").assertIsDisplayed()
    }
}
