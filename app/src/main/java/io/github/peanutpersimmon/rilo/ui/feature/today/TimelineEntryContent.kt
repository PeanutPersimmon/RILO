package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.peanutpersimmon.rilo.presentation.today.TimelineEntryUiModel
import io.github.peanutpersimmon.rilo.ui.components.TimelineDetailReveal
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun TimelineEntryContent(entry: TimelineEntryUiModel, zoom: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
            Text(entry.title, Modifier.weight(1f), style = AppTheme.type.fieldLabel,
                textDecoration = if (entry.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val duration = entry.durationLabel
            duration?.let {
                Text(it, style = AppTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        TimelineDetailReveal(TimelineZoomPolicy.details(entry), zoom)
    }
}
