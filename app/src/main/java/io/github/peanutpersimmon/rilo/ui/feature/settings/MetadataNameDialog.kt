package io.github.peanutpersimmon.rilo.ui.feature.settings

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import io.github.peanutpersimmon.rilo.ui.components.AppDialog
import io.github.peanutpersimmon.rilo.ui.components.AppTextField
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorTextAction
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** MyLibrary name-dialog flow, with RILO's unified dialog and grayscale error text. */
@Composable
fun MetadataNameDialog(title: String, initial: String, onDismiss: () -> Unit,
    onConfirm: (String, () -> Unit) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val model = LocalMetadataViewModel.current
    LaunchedEffect(Unit) { model?.clearError() }
    AppDialog(title, onDismiss, actions = {
        EditorTextAction("取消", onDismiss)
        EditorTextAction("确定", { onConfirm(name.trim(), onDismiss) }, enabled = name.isNotBlank())
    }) {
        AppTextField(name, { name = it }, "输入名称", singleLine = true,
            stacked = true, showLabel = false)
        LocalMetadataState.current.error?.let { Text(it, style = AppTheme.type.caption) }
    }
}
