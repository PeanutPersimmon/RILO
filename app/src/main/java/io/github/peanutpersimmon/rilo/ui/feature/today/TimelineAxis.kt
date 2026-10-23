package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
internal fun TimelineAxis(height: Dp, timeWidth: Dp, modifier: Modifier = Modifier) {
    Row(modifier.height(height)) {
        Box(Modifier.width(timeWidth))
        Box(Modifier.width(AppTheme.dimens.timelineLaneWidth).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(AppTheme.dimens.border).fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
internal fun TimelineMarker(start: String, end: String?, timeWidth: Dp, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    showLabel: Boolean = true, deadline: Boolean = false, coincidentPoint: Boolean = false) {
    Row(modifier.semantics { contentDescription = if (deadline) "截止 $start" else start },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(timeWidth).padding(end = AppTheme.dimens.spaceXs), horizontalAlignment = Alignment.End) {
            if (showLabel) TimelineTimeText(if (deadline && coincidentPoint) "⚑ $start" else start)
            if (showLabel) end?.let { TimelineTimeText(it, secondary = true) }
        }
        Box(
            Modifier.width(AppTheme.dimens.timelineLaneWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (deadline && !coincidentPoint) Icon(Icons.Outlined.Flag, null, Modifier.size(AppTheme.dimens.spaceM), tint = color)
            else Box(Modifier.size(AppTheme.dimens.timelineDotSize).background(color, CircleShape))
        }
    }
}

@Composable
internal fun TimelineTimeText(value: String, secondary: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        value, modifier = modifier, style = AppTheme.type.timelineTime,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, textAlign = TextAlign.End,
    )
}
