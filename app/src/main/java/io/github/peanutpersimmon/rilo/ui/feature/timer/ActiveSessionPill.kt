package io.github.peanutpersimmon.rilo.ui.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.presentation.timer.TimerUiState
import io.github.peanutpersimmon.rilo.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActiveSessionPill(
    state: TimerUiState.Active,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sleep = state.kind != SessionKind.ACTIVITY
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(max = 232.dp),
        shape = AppTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (sleep) Icons.Outlined.Bedtime else Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            if (sleep) {
                val start = Instant.ofEpochMilli(state.startAtMillis)
                    .atZone(io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId.current)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    "睡眠记录中 · $start",
                    style = AppTheme.type.secondary,
                    maxLines = 1,
                )
            } else {
                Text(
                    state.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = AppTheme.type.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(state.elapsed.clockText, style = AppTheme.type.secondary, maxLines = 1)
            }
        }
    }
}

@Composable
fun TimerControlSheet(
    state: TimerUiState.Active,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onStop: () -> Unit,
) {
    AppBottomSheet(
        title = "当前计时",
        bottomVisual = io.github.peanutpersimmon.rilo.ui.components.SheetBottomVisual.Special,
        onDismissRequest = onDismiss,
        headerAction = {
            TextButton(onClick = { onEdit(state.sessionId) }) {
                Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(18.dp))
                Text("编辑")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.spaceS),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.title,
                    modifier = Modifier.weight(1f),
                    style = AppTheme.type.itemTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(state.elapsed.clockText, style = AppTheme.type.itemTitle)
            }
            state.errorMessage?.let {
                Text(it, style = AppTheme.type.caption, color = MaterialTheme.colorScheme.error)
            }
            TimerControls(
                isStopping = state.isStopping,
                onStop = onStop,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
