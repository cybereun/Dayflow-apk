package com.cybereun.dayflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import androidx.core.view.WindowCompat

class PlannerUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun dailyHeadingAndTaskEntryAreAvailable() {
        val label="안드로이드 확인 "+java.util.UUID.randomUUID().toString().take(8)
        rule.waitUntil(15000){rule.onAllNodesWithText("TASKS").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("new-task").performScrollTo().performTextInput(label)
        rule.onNodeWithContentDescription("할 일 추가").performClick()
        rule.onNodeWithText(label).assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15000){rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(label).assertExists()
    }
    @Test fun systemBarIconsAreReadableOnLightPaper() {
        rule.runOnUiThread {
            assertTrue(WindowCompat.getInsetsController(rule.activity.window,rule.activity.window.decorView).isAppearanceLightStatusBars)
        }
    }
}
