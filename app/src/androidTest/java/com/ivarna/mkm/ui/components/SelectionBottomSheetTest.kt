package com.ivarna.mkm.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.mkm.ui.theme.MKMTheme
import com.ivarna.mkm.ui.viewmodel.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedSelectionKeepsSheetOpenAndShowsError() {
        composeRule.setContent {
            var error by remember { mutableStateOf<String?>(null) }
            var applying by remember { mutableStateOf(false) }
            MKMTheme(appTheme = AppTheme.LIGHT) {
                SelectionBottomSheet(
                    title = "Frequency",
                    items = listOf("300", "1200", "2800"),
                    selectedItem = "2800",
                    onDismiss = {},
                    onItemSelected = {
                        applying = false
                        error = "Write failed"
                    },
                    isApplying = applying,
                    errorMessage = error
                )
            }
        }

        composeRule.onNodeWithText("300").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Write failed").assertIsDisplayed()
        composeRule.onNodeWithText("300").assertIsDisplayed()
    }

    @Test
    fun successfulSelectionUsesOneRowClickCallback() {
        var clicks = 0
        composeRule.setContent {
            MKMTheme(appTheme = AppTheme.LIGHT) {
                SelectionBottomSheet(
                    title = "Governor",
                    items = listOf("schedutil", "performance"),
                    selectedItem = "schedutil",
                    onDismiss = {},
                    onItemSelected = { clicks++ }
                )
            }
        }

        composeRule.onNodeWithText("performance").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }
}
