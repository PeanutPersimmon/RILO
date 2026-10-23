package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.text.style.TextOverflow
import io.github.peanutpersimmon.rilo.ui.components.AppSnackbar
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** One sheet shell for both creation and editing; the caller keeps Today composed. */
@Composable
fun EditorLayout(
    title: String,
    canSave: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteEnabled: Boolean = true,
    extraActions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AppBottomSheet(title = null, onDismissRequest = onBack,
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Special) {
        Row(Modifier.fillMaxWidth().padding(bottom = AppTheme.dimens.spaceXs), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = AppTheme.type.sheetTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            extraActions()
            EditorIconAction(Icons.Outlined.Check, "保存", onSave, canSave)
            onDelete?.let { EditorIconAction(Icons.Outlined.Delete, "删除", it, deleteEnabled) }
            EditorIconAction(Icons.Outlined.Close, "关闭", onBack)
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
        ) { content() }
        SnackbarHost(snackbarHostState) { AppSnackbar(it) }
    }
}
