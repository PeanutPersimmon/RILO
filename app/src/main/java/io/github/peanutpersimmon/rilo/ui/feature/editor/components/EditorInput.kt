package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun EditorInput(value: String, onChange: (String) -> Unit, description: Boolean = false) {
    val style = if (description) AppTheme.type.editorDescription else AppTheme.type.editorInput
    val hint = if (description) "细节" else "标题"
    BasicTextField(
        value, onChange,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = AppTheme.dimens.editorInputMinHeight)
            .semantics { contentDescription = if (description) "细节" else "标题，必填" },
        singleLine = !description,
        maxLines = if (description) 3 else 1,
        textStyle = style.copy(color = if (description) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { field ->
            Box(Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.spaceXs), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                field()
            }
        },
    )
}
