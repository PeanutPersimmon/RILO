package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.presentation.editor.EditorChoice
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

fun selectableChoices(all: List<EditorChoice>, selectedIds: Set<Long>): List<EditorChoice> =
    all

@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    sheetStyle: Boolean = false,
    showSelectionIndicator: Boolean = sheetStyle,
    onClick: () -> Unit,
) {
    val d = AppTheme.dimens
    Box(
        modifier = Modifier.fillMaxWidth()
            .then(if (sheetStyle) Modifier.heightIn(min = d.sheetRowVisualHeight - d.spaceXs) else Modifier.height(38.dp))
            .then(if (sheetStyle) Modifier else Modifier.clip(AppTheme.shapes.small)
                .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surface))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (sheetStyle) d.sheetRowVisualHeight - d.spaceXs else 38.dp)
                .padding(horizontal = d.spaceS,
                    vertical = if (sheetStyle) d.spaceS - d.spaceXs / 2 else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                Modifier.weight(1f),
                style = if (sheetStyle) AppTheme.type.body else AppTheme.type.fieldValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (sheetStyle) Int.MAX_VALUE else 1,
                overflow = if (sheetStyle) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            if (showSelectionIndicator) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null,
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
