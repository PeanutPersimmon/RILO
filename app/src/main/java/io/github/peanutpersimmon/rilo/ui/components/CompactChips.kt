package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** Shared visual scale for category keywords and child tags. */
@Composable
fun CompactChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = AppTheme.dimens.tagChipHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected },
        shape = AppTheme.shapes.pill,
        color = if (selected) colors.onSurface else colors.surfaceContainerLow,
        border = if (selected) null else BorderStroke(AppTheme.dimens.border, colors.outlineVariant),
    ) {
        Box(
            Modifier.padding(horizontal = if (primary) AppTheme.dimens.tagRootChipHorizontalPadding
                else AppTheme.dimens.tagChipHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = if (primary) AppTheme.type.fieldLabel else AppTheme.type.editorDescription,
                color = if (selected) colors.surface else colors.onSurface,
                softWrap = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(vertical = AppTheme.dimens.spaceXs / 2),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
    ) { content() }
}
