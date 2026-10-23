package io.github.peanutpersimmon.rilo.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceAgentDiagnosticsStore
import io.github.peanutpersimmon.rilo.ui.feature.settings.AiDiagnosticsDialog
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiDiagnosticsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentToolArgumentsAndResultCanBeCopied() {
        VoiceAgentDiagnosticsStore.begin()
        VoiceAgentDiagnosticsStore.rawArguments("""{"start_at":"2026-09-24T12:00:00+08:00"}""")
        VoiceAgentDiagnosticsStore.mappedAction("CreateEvent")
        VoiceAgentDiagnosticsStore.validationResult("success")
        compose.setContent { RidoTheme { AiDiagnosticsDialog {} } }
        compose.onNodeWithText("AI 诊断").assertExists()
        compose.onNodeWithText("复制").performClick()
        compose.onNodeWithText("已复制").assertExists()
        compose.runOnIdle {
            val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(copied.contains("rawArguments ="))
            assertTrue(copied.contains("mappedAction = CreateEvent"))
            assertTrue(copied.contains("validationResult = success"))
        }
    }
}
