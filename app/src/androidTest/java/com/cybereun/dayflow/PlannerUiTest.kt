package com.cybereun.dayflow

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
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
    @Test fun appHasInternetPermissionForEncryptedSync() {
        assertTrue(rule.activity.checkSelfPermission(Manifest.permission.INTERNET)==PackageManager.PERMISSION_GRANTED)
    }
    @Test fun appDeclaresTheDayflowLauncherIcon() {
        val info=rule.activity.packageManager.getApplicationInfo(rule.activity.packageName,0)
        assertTrue(info.icon!=0)
        assertEquals("ic_launcher",rule.activity.resources.getResourceEntryName(info.icon))
    }
    @Test fun dailyTabletUsesOpenSpreadLayout() {
        rule.runOnUiThread { rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000){rule.onAllNodesWithTag("daily-open-spread").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("daily-left-page").assertExists()
        rule.onNodeWithTag("daily-right-page").assertExists()
        rule.onNodeWithTag("daily-binding").assertExists()
    }
    @Test fun settingsOfferPlannerDdayBackupAndEncryptedSyncControls() {
        val name="두 번째 플래너 "+java.util.UUID.randomUUID().toString().take(6)
        rule.waitUntil(15000){rule.onAllNodesWithText("설정").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("설정").performClick()
        rule.waitUntil(5000){rule.onAllNodesWithText("PLANNERS").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("D-DAY").assertExists()
        rule.onNodeWithText("백업 ZIP 만들기").assertExists()
        rule.onNodeWithText("새 동기화 시작").assertExists()
        rule.onNodeWithTag("new-book").performScrollTo().performTextInput(name)
        rule.onNodeWithText("+ 추가").performClick()
        rule.waitUntil(5000){rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(name).assertExists()
    }
}
