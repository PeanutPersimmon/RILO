package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPermissionRowsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun statusOnlyPermissionRowsRemainClickableWithoutExplanation() {
        var notifications = 0
        var exact = 0
        compose.setContent {
            RidoTheme {
                Column {
                    SettingRow("通知权限", status = "未开启") { notifications++ }
                    SettingRow("准时提醒", status = "已允许") { exact++ }
                }
            }
        }
        compose.onNodeWithText("未开启").assertExists()
        compose.onNodeWithText("已允许").assertExists()
        compose.onNodeWithText("允许 RILO 尽量在设定时刻准时发送提醒。").assertDoesNotExist()
        compose.onNodeWithText("提醒仍会发送，但可能被系统延后。").assertDoesNotExist()
        compose.onNodeWithText("通知权限").performClick()
        compose.onNodeWithText("准时提醒").performClick()
        compose.runOnIdle { assertEquals(1, notifications); assertEquals(1, exact) }
    }
}
