package io.github.peanutpersimmon.rilo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.*
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EditorMetadataTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unsetMetadataIsIconOnlyAndSetValueIsVisible() {
        val value = mutableStateOf<String?>(null)
        compose.setContent {
            RidoTheme { EditorTool(Icons.Outlined.FolderOpen, "分类", value.value, { value.value = "自定义分类" }) }
        }
        compose.onNodeWithText("分类").assertDoesNotExist()
        compose.onNodeWithContentDescription("分类").performClick()
        compose.onNodeWithText("自定义分类", useUnmergedTree = true).assertExists()
    }
    @Test fun dateAndTimeIsOneTextLineAt320dp() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                RidoTheme {
                    Column(Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                        EditorTimeModule {
                            DateTimeField("开始", EditorTimeRole.START, LocalDate.of(2026, 9, 21), LocalTime.of(6, 6), {})
                            DateTimeField("结束", EditorTimeRole.END, LocalDate.of(2026, 9, 21), LocalTime.of(8, 20), {})
                        }
                    }
                }
            }
        }
        listOf("9/21 06:06", "9/21 08:20").forEach { text ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(1, results.single().lineCount)
            assertFalse(results.single().hasVisualOverflow)
        }
    }

}
