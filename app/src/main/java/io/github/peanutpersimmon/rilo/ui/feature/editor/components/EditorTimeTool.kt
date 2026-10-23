package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun EditorTimeTool(
    icon: ImageVector,
    label: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    textStyle: TextStyle = AppTheme.type.editorDescription,
    iconTestTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier.heightIn(min = AppTheme.dimens.minimumTouchTarget),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(AppTheme.dimens.minimumTouchTarget)
                .widthIn(min = AppTheme.dimens.minimumTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = listOfNotNull(label, summary).joinToString("，") }
                .padding(horizontal = AppTheme.dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        ) {
            Icon(
                icon,
                null,
                Modifier
                    .size(AppTheme.dimens.editorTimeIconSize)
                    .then(iconTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary?.let {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            onClear?.let { clear ->
                // Clear button participates in the Row layout flow so it never
                // overlays the date/time text. Its own clickable consumes the
                // tap so the parent field click does not fire.
                Box(
                    Modifier
                        .size(AppTheme.dimens.minimumTouchTarget)
                        .clickable(role = Role.Button, onClick = clear)
                        .semantics { contentDescription = "清除$label" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        null,
                        Modifier.size(AppTheme.dimens.editorIconSize),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}
