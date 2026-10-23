package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rilo.ui.components.AppClickableSurface
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun EditorTextAction(text: String, onClick: () -> Unit, enabled: Boolean = true,
    compactSheetAction: Boolean = false) {
    if (compactSheetAction) {
        Box(Modifier.clickable(enabled = enabled, onClick = onClick)) {
            Text(text, Modifier.padding(horizontal = AppTheme.dimens.spaceS),
                style = AppTheme.type.fieldLabel,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false)
        }
        return
    }
    AppClickableSurface(
        onClick = onClick, enabled = enabled,
        shape = AppTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text, Modifier.padding(horizontal = AppTheme.dimens.spaceS),
            style = AppTheme.type.fieldLabel,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
fun EditorSection(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
        HorizontalDivider(color = AppTheme.colors.border)
        content()
    }
}
