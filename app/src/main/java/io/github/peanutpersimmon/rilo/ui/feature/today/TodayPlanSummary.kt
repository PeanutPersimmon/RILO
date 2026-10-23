package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.presentation.today.TodayPlanSummaryUiModel
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import kotlin.math.roundToInt

internal object TimelineSummaryTypography {
    val collapsedHeight: Dp @Composable get() = AppTheme.dimens.timelineReminderHeight
    @Composable fun summary() = AppTheme.type.timelineReminder
    @Composable fun item() = AppTheme.type.reminderItemTitle
    @Composable fun metadata() = AppTheme.type.reminderMetadata
}

private fun Modifier.revealHeight(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val visibleHeight = (placeable.height * fraction.coerceIn(0f, 1f)).roundToInt()
    layout(placeable.width, visibleHeight) {
        placeable.placeRelative(0, 0)
    }
}

/** A standalone overlay. Its header toggles expansion; its body only scrolls content. */
@Composable
internal fun PullSummarySurface(
    summaryText: String,
    state: TodayPullState,
    maxExpandedBodyHeight: Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val listScroll = rememberScrollState()
    Surface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.spaceXs)
            .semantics {
                stateDescription = if (state.fraction == 0f) "已收起" else "已展开"
            },
        shape = AppTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TimelineSummaryTypography.collapsedHeight)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = AppTheme.dimens.spaceS),
                contentAlignment = androidx.compose.ui.Alignment.CenterStart,
            ) {
                Text(
                    summaryText,
                    style = TimelineSummaryTypography.summary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxExpandedBodyHeight)
                    .revealHeight(state.fraction)
                    .clipToBounds(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxExpandedBodyHeight)
                        .verticalScroll(listScroll, enabled = state.fullyExpanded)
                        .padding(
                            horizontal = AppTheme.dimens.spaceS,
                            vertical = AppTheme.dimens.spaceXs,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun TodayPlanSummary(
    summary: TodayPlanSummaryUiModel,
    state: TodayPullState,
    maxExpandedBodyHeight: Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: (io.github.peanutpersimmon.rilo.presentation.today.TodayPlanItemUiModel) -> Unit = {},
) {
    PullSummarySurface(
        summaryText = "${summary.remainingCount} 项待办",
        state = state,
        maxExpandedBodyHeight = maxExpandedBodyHeight,
        onToggle = onToggle,
        modifier = modifier,
    ) {
        summary.items.forEach { item ->
            val categoryColors = item.categoryColorKey?.let {
                resolveCategoryColors(it, MaterialTheme.colorScheme)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) },
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
            ) {
                if (categoryColors != null) Box(Modifier.padding(top = 5.dp).size(5.dp)
                    .background(categoryColors.base, CircleShape))
                Text(
                    item.title,
                    Modifier.weight(1f),
                    style = TimelineSummaryTypography.item(),
                    textDecoration = if (item.completed) {
                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                    } else {
                        null
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                (item.detail ?: item.timeLabel)?.let { detail ->
                    Text(
                        detail,
                        style = TimelineSummaryTypography.metadata(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
