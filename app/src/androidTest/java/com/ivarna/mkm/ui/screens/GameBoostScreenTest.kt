package com.ivarna.mkm.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivarna.mkm.data.model.GameBoostComponent
import com.ivarna.mkm.data.model.GameBoostState
import com.ivarna.mkm.ui.theme.MKMTheme
import com.ivarna.mkm.ui.viewmodel.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameBoostScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun toggleRowContainsExactlyOneSwitch() {
        composeRule.setContent {
            MKMTheme(appTheme = AppTheme.LIGHT) {
                GameBoostToggleRow(checked = false, enabled = true, onCheckedChange = {})
            }
        }
        composeRule.onAllNodesWithContentDescription("Game Boost").assertCountEquals(1)
    }

    @Test
    fun thermalLimitedStatusDoesNotReportReleasedMaxLocksAsApplied() {
        composeRule.setContent {
            MKMTheme(appTheme = AppTheme.LIGHT) {
                GameBoostStatusCard(
                    state = GameBoostState.ThermalLimited(
                        stillApplied = setOf(GameBoostComponent.CPU_GOVERNOR, GameBoostComponent.GPU_GOVERNOR),
                        released = setOf(GameBoostComponent.CPU_MAX_LOCK, GameBoostComponent.GPU_MAX_LOCK)
                    ), capabilities = null
                )
            }
        }
        composeRule.onAllNodesWithText("Not applied").assertCountEquals(2)
        composeRule.onNodeWithText("Severe thermal status: maximum-clock locks were released and will not be relocked automatically.").assertIsDisplayed()
    }

    @Test
    fun recoveryStatusIsVisible() {
        composeRule.setContent {
            MKMTheme(appTheme = AppTheme.LIGHT) {
                GameBoostStatusCard(GameBoostState.RecoveryRequired("restore failed", listOf("CPU_MAX_LOCK")), null)
            }
        }
        composeRule.onNodeWithText("Recovery required. Restore the saved tuning state before disabling.").assertIsDisplayed()
    }
}
