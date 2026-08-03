package com.example.familytreeplatform.feature.support

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.example.familytreeplatform.BuildConfig
import com.example.familytreeplatform.ui.theme.FamilyTreePlatformTheme
import org.junit.Rule
import org.junit.Test

class SupportScreensInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutShowsIdentityBetaVersionAndCopyrightFooter() {
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                AboutScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText("Tentang aplikasi").assertIsDisplayed()
        composeRule.onNodeWithText("Merangkai jejak, menyatukan trah").assertIsDisplayed()
        composeRule.onNodeWithText(applicationVersionLabel(BuildConfig.VERSION_NAME), substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("© sadar@studio 2026").assertIsDisplayed()
    }

    @Test
    fun helpCoversNavigationDataEntryCollaborationAndExport() {
        composeRule.setContent {
            FamilyTreePlatformTheme(dynamicColor = false) {
                HelpScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText("Petunjuk penggunaan").assertIsDisplayed()
        composeRule.onNodeWithText("Menjelajahi pohon").assertIsDisplayed()
        composeRule.onNodeWithText("Menambah person").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Menambah hubungan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("support-page-list").performScrollToIndex(12)
        composeRule.onNodeWithText("Undangan dan peran").assertIsDisplayed()
        composeRule.onNodeWithTag("support-page-list").performScrollToIndex(14)
        composeRule.onNodeWithText("Export pohon").assertIsDisplayed()
        composeRule.onNodeWithText("© sadar@studio 2026").assertIsDisplayed()
    }
}
