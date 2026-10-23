package io.github.peanutpersimmon.rilo.ui.feature.editor.event

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.*
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import java.time.LocalDate

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun EventEditorPreview() = RidoTheme {
    EditorLayout("新建日程", true, remember { SnackbarHostState() }, {}, {}) {
        TitleField("大组会", {})
        NoteField("", {})
        EditorTimeModule { DateField("日程开始", LocalDate.of(2026, 10, 14), {}) }
        EditorToolGroup {
            CategoryPicker(emptyList(), null) {}
        }
    }
}
