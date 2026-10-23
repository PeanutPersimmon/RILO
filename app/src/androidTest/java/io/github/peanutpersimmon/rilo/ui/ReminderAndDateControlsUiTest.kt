package io.github.peanutpersimmon.rilo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.components.CompactDateNavigation
import io.github.peanutpersimmon.rilo.ui.components.TimelinePointItem
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.ReminderBell
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderAndDateControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bellStateUsesActiveAndInactiveIcons() {
        compose.setContent {
            RidoTheme {
                Column {
                    ReminderBell("计划提醒", null) {}
                    ReminderBell("截止提醒", 10) {}
                    ReminderBell("自定义提醒", 17) {}
                }
            }
        }
        compose.onNodeWithTag("reminder-bell-计划提醒-off").assertExists()
        compose.onNodeWithTag("reminder-bell-截止提醒-on").assertExists()
        compose.onNodeWithTag("reminder-bell-计划提醒-on").assertDoesNotExist()
        compose.onNodeWithText("自定义 · 17 分钟").assertExists()
    }


    @Test fun arrowTargetsAreWideAndDateLabelOpensDate() {
        var previous = 0
        var opened = 0
        compose.setContent {
            RidoTheme {
                CompactDateNavigation("10/5", { previous++ }, {}, { opened++ },
                    Modifier.width(320.dp), touchTarget = 44.dp)
            }
        }
        val arrow = compose.onNodeWithTag("date-navigation-previous").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag("date-navigation-label").fetchSemanticsNode().boundsInRoot
        assertTrue(arrow.width >= 40f * compose.density.density)
        assertTrue(label.width > arrow.width)
        compose.onNodeWithTag("date-navigation-label").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithTag("date-navigation-previous").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, previous) }
    }
}
