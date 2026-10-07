package com.cybereun.dayflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PlannerUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun dailyHeadingAndTaskEntryAreAvailable() {
        rule.waitUntil(15000){rule.onAllNodesWithText("TASKS").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("new-task").performTextInput("안드로이드 확인")
        rule.onNodeWithContentDescription("할 일 추가").performClick()
        rule.onNodeWithText("안드로이드 확인").assertExists()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15000){rule.onAllNodesWithText("안드로이드 확인").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("안드로이드 확인").assertExists()
    }
}
