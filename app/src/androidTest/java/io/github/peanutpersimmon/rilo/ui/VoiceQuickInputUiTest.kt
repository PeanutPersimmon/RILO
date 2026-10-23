package io.github.peanutpersimmon.rilo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.peanutpersimmon.rilo.ui.components.AppNavigationBar
import io.github.peanutpersimmon.rilo.ui.components.AppNavigationItem
import io.github.peanutpersimmon.rilo.ui.components.picker.PickerHost
import io.github.peanutpersimmon.rilo.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorTimeRole
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.SplitDateTimeField
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VoiceQuickInputUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun microphoneShortAndLongPressAreActionsAndNavigationHasNoTextLabels() {
        var shortPresses = 0
        var longPresses = 0
        var releases = 0
        var destinationChanges = 0
        val labels = listOf("Today", "Plan", "Statistics", "Settings")
        compose.setContent {
            RidoTheme {
                AppNavigationBar(labels.map { AppNavigationItem(it, Icons.Outlined.Home) }, 0,
                    onItemSelected = { destinationChanges++ },
                    onVoiceClick = { shortPresses++ }, onVoiceLongClick = { longPresses++ },
                    onVoiceRelease = { releases++ })
            }
        }
        labels.forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
            compose.onNodeWithContentDescription(it).assertExists()
        }
        compose.onNodeWithTag("quick_input_microphone").performClick()
        compose.onNodeWithTag("quick_input_microphone").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, shortPresses)
            assertEquals(1, longPresses)
            assertEquals(1, releases)
            assertEquals(0, destinationChanges)
        }
    }

    @Test fun combinedDateTimeFieldOpensPickerAndReturnsBothValues() {
        val day = LocalDate.of(2026, 9, 22)
        val time = LocalTime.of(19, 0)
        var dateChanges = 0
        var selectedTime: LocalTime? = null
        compose.setContent {
            RidoTheme {
                val picker = rememberPickerHostState()
                SplitDateTimeField("开始", EditorTimeRole.START, day, time, picker,
                    onDateChange = { dateChanges++ }, onTimeChange = { selectedTime = it })
                PickerHost(picker)
            }
        }
        compose.onNodeWithContentDescription("开始，9/22 19:00").performClick()
        compose.onNodeWithContentDescription("上个月").assertExists()
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(1, dateChanges); assertEquals(time, selectedTime) }
    }
}
