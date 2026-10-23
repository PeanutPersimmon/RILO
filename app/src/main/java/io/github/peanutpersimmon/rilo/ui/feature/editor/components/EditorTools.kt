package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun EditorIconAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true,
    compactSheetAction: Boolean = false) {
    Box(
        (if (compactSheetAction) Modifier.size(AppTheme.dimens.minimumTouchTarget, AppTheme.dimens.editorIconSize)
        else Modifier.size(AppTheme.dimens.minimumTouchTarget))
            .clickable(
                enabled = enabled, role = Role.Button, onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(AppTheme.dimens.editorIconSize),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.85f else 0.3f))
    }
}

/** Shared selected-metadata chip used by categories and tags in editor footers. */
@Composable
fun EditorSelectionChip(
    icon: ImageVector,
    label: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val d = AppTheme.dimens
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(d.editorRowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "$label，$text"
                selected = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.height(d.tagChipHeight),
            shape = AppTheme.shapes.pill,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            border = BorderStroke(d.border, Color.Transparent),
        ) {
            Row(
                Modifier.padding(horizontal = d.spaceS),
                horizontalArrangement = Arrangement.spacedBy(d.spaceMicro),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(d.editorIconSize),
                    tint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f),
                )
                Text(
                    text,
                    style = AppTheme.type.editorTool,
                    color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Empty metadata is a transparent touch target; only real values get a pill. */
@Composable
fun EditorTool(
    icon: ImageVector,
    label: String,
    summary: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (summary.isNullOrBlank()) {
        EditorIconAction(icon, label, onClick, enabled)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Fixed row-height token (not a min-height) so every footer child is exactly the
        // same height and their bottom edges coincide by construction. The pill is centred
        // inside this touch target rather than being stretched to fill it.
        Box(
            Modifier.height(AppTheme.dimens.editorRowHeight)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label，$summary" },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                    .background(tint.copy(alpha = 0.08f))
                    .padding(horizontal = AppTheme.dimens.spaceS, vertical = AppTheme.dimens.spaceXs),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceMicro),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(AppTheme.dimens.editorIconSize), tint = tint)
                Text(summary, style = AppTheme.type.editorTool,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        onClear?.let { EditorIconAction(Icons.Outlined.Close, "清除$label", it, enabled) }
    }
}

@Composable
fun EditorToolGroup(content: @Composable () -> Unit) {
    // Footer chips share one fixed row height, so centring them vertically keeps every
    // chip's bottom edge on the same line regardless of its inner content height.
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceMicro),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

@Composable
fun EditorTimeModule(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.editorTimeRowGap),
        content = content,
    )
}
