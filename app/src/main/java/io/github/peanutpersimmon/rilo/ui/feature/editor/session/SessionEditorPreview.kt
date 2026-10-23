package io.github.peanutpersimmon.rilo.ui.feature.editor.session

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.*
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import java.time.LocalTime

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun SessionEditorPreview() = RidoTheme {
    EditorLayout("编辑记录", true, remember { SnackbarHostState() }, {}, {}, onDelete = {}) {
        TitleField("做 PPT", {})
        NoteField("", {})
        EditorTimeModule {
            TimeField("开始", LocalTime.of(14, 0), {})
            TimeField("结束", LocalTime.of(15, 30), {})
        }
        EditorToolGroup {
            RecordCategoryPicker(emptyList(), null, io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY) { _, _ -> }
        }
    }
}
