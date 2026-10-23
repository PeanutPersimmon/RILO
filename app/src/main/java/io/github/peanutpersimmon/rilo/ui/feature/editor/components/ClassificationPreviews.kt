package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rilo.presentation.editor.EditorChoice
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme

private val previewChoices = listOf(EditorChoice(1, "工作"), EditorChoice(2, "科研", true))

@Preview(showBackground = true)
@Composable
private fun CategoryPickerPreview() = RidoTheme {
    CategoryPicker(previewChoices, 2) {}
}

