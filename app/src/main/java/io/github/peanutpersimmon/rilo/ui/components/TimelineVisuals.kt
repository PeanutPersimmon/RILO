package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

/** The two surfaces used by both Today and Plan/Todo & Done. */
enum class TimelineVisualKind { PLANNED, ACTUAL, NAP }
enum class TimelinePointSide { PLANNED, ACTUAL }

const val TimelineCardLayer = 1f
const val TimelineAxisLayer = 2f
const val TimelinePointLayer = 2.5f
const val TimelineNowLayer = 3f

fun formatTimelineDuration(startMillis: Long, endMillis: Long): String {
    val minutes = ((endMillis - startMillis).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 60L -> "${minutes}m"
        minutes % 60L == 0L -> "${minutes / 60L}h"
        else -> "${minutes / 60L}h${minutes % 60L}m"
    }
}

@Composable
fun timelineTimeLabelHeight(): Dp {
    val lineHeight = AppTheme.type.timelineTime.lineHeight
    return with(LocalDensity.current) { lineHeight.toDp() }
}

/** Reminder height + visible gap + half-label protection, or label protection alone. */
@Composable
fun timelineTopBoundary(reminderVisible: Boolean): Dp {
    val labelHalfHeight = timelineTimeLabelHeight() / 2
    return labelHalfHeight + if (reminderVisible) {
        AppTheme.dimens.timelineReminderHeight + AppTheme.dimens.reminderToTimelineGap
    } else {
        0.dp
    }
}

internal enum class TimelineBoundaryPlacement { CENTERED, INSIDE_BOTTOM }

/** Positions boundary decoration without moving the visual axis endpoint away from its guide. */
internal fun timelineBoundaryElementTop(
    anchor: Dp,
    elementHeight: Dp,
    placement: TimelineBoundaryPlacement,
): Dp = when (placement) {
    TimelineBoundaryPlacement.CENTERED -> anchor - elementHeight / 2
    TimelineBoundaryPlacement.INSIDE_BOTTOM -> anchor - elementHeight
}

/** The text styles and padding are captured once so measurements can be memoized across pinch frames. */
internal class TimelineContentMeasurer(
    private val titleStyle: TextStyle,
    private val durationStyle: TextStyle,
    private val metadataStyle: TextStyle,
    private val horizontalPaddingPx: Float,
    private val verticalPaddingPx: Float,
) {
internal fun height(
    title: String,
    duration: String?,
    details: List<String>,
    widthPx: Int,
    detailProgress: Float,
    measurer: TextMeasurer,
    point: Boolean = false,
): Float {
    val horizontalPadding = if (point) 0f else horizontalPaddingPx
    val verticalPadding = verticalPaddingPx
    val gap = horizontalPaddingPx
    val durationLayout = duration?.takeIf(String::isNotBlank)?.let {
        measurer.measure(it, durationStyle, maxLines = 1)
    }
    val titleWidth = (widthPx - 2f * horizontalPadding -
        if (durationLayout == null) 0f else durationLayout.size.width + gap).toInt().coerceAtLeast(1)
    val titleHeight = measurer.measure(
        title,
        titleStyle,
        constraints = Constraints(maxWidth = titleWidth),
        softWrap = true,
    ).size.height.toFloat()
    val firstRow = maxOf(titleHeight, durationLayout?.size?.height?.toFloat() ?: 0f)
    val visibleDetails = details.filter(String::isNotBlank)
    val detailWidth = (widthPx - 2f * horizontalPadding).toInt().coerceAtLeast(1)
    val detailHeight = visibleDetails.sumOf {
        measurer.measure(it, metadataStyle,
            constraints = Constraints(maxWidth = detailWidth), softWrap = true,
        ).size.height.toDouble()
    }.toFloat() + (visibleDetails.size - 1).coerceAtLeast(0) * verticalPadding
    val progress = detailProgress.coerceIn(0f, 1f)
    if (point) return firstRow + detailHeight * progress
    return firstRow + 2f * verticalPadding +
        if (visibleDetails.isEmpty() || progress == 0f) 0f
        else (detailHeight + verticalPadding) * progress
}
}

@Composable
internal fun rememberTimelineContentMeasurer(): TimelineContentMeasurer {
    val type = AppTheme.type
    val dimensions = AppTheme.dimens
    val density = LocalDensity.current
    return remember(type, dimensions, density) {
        TimelineContentMeasurer(
            type.timelineTitle, type.timelineDuration, type.timelineMetadata,
            with(density) { dimensions.spaceXs.toPx() },
            with(density) { dimensions.spaceMicro.toPx() },
        )
    }
}

internal fun naturalTimelineContentHeight(
    titleRowHeight: Float,
    detailCount: Int,
    detailLineHeight: Float,
    detailProgress: Float,
    verticalPadding: Float,
): Float {
    require(titleRowHeight > 0f && detailCount >= 0 && verticalPadding >= 0f)
    val progress = detailProgress.coerceIn(0f, 1f)
    val detailHeight = if (detailCount == 0 || progress == 0f) 0f else {
        (detailCount * detailLineHeight +
            (detailCount - 1) * verticalPadding) * progress + verticalPadding
    }
    return titleRowHeight + 2f * verticalPadding + detailHeight
}

/** Expands only the transparent hit target; [content] keeps the resolved visual card geometry. */
@Composable
fun TimelineBlockPlacement(
    top: Dp,
    visualHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandTouchTarget: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val targetHeight = if (expandTouchTarget) {
        maxOf(visualHeight, AppTheme.dimens.minimumTouchTarget)
    } else {
        visualHeight
    }
    Box(
        modifier
            .offset(y = top)
            .height(targetHeight)
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        content(Modifier.fillMaxWidth().height(visualHeight).align(Alignment.TopStart))
    }
}

@Composable
fun TimelineTimeBlock(
    title: String,
    duration: String?,
    details: List<String>,
    kind: TimelineVisualKind,
    modifier: Modifier = Modifier,
    showCompactTitle: Boolean = false,
    accentColor: Color? = null,
    detailProgress: Float = 1f,
    backgroundColor: Color? = null,
    titleColor: Color? = null,
    metadataColor: Color? = null,
) {
    val background = backgroundColor ?: when (kind) {
        TimelineVisualKind.PLANNED, TimelineVisualKind.NAP -> AppTheme.colors.plannedSurface
        TimelineVisualKind.ACTUAL -> MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier.clipToBounds(),
        shape = AppTheme.shapes.small,
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints {
            val blockHeight = maxHeight
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val verticalPadding = AppTheme.dimens.spaceMicro * 2
            val durationWidth = duration?.takeIf(String::isNotBlank)?.let {
                measurer.measure(it, AppTheme.type.timelineDuration, maxLines = 1).size.width
            } ?: 0
            val titleWidth = with(density) {
                (maxWidth - AppTheme.dimens.spaceXs * 2 -
                    if (durationWidth == 0) 0.dp else durationWidth.toDp() + AppTheme.dimens.spaceXs)
                    .toPx().toInt().coerceAtLeast(1)
            }
            val titleHeight = with(density) {
                measurer.measure(
                    if (kind == TimelineVisualKind.NAP) "☾ $title" else title,
                    AppTheme.type.timelineTitle,
                    constraints = Constraints(maxWidth = titleWidth),
                ).size.height.toDp()
            }
            val durationHeight = with(density) { AppTheme.type.timelineDuration.lineHeight.toDp() }
            val firstLineHeight = maxOf(titleHeight, durationHeight)
            val detailHeight = (
                blockHeight - verticalPadding - firstLineHeight - AppTheme.dimens.spaceMicro
            ).coerceAtLeast(0.dp)
            if (kind == TimelineVisualKind.ACTUAL || accentColor != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(AppTheme.dimens.border * 2)
                        .fillMaxHeight()
                        .background(accentColor ?: AppTheme.colors.actual),
                )
            }
            if (showCompactTitle || blockHeight >= AppTheme.dimens.spaceM) Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = AppTheme.dimens.spaceXs,
                        vertical = AppTheme.dimens.spaceMicro,
                    ),
                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceMicro),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
                ) {
                    Text(
                        if (kind == TimelineVisualKind.NAP) "☾ $title" else title,
                        Modifier.weight(1f),
                        style = AppTheme.type.timelineTitle,
                        color = titleColor ?: Color.Unspecified,
                        overflow = TextOverflow.Clip,
                    )
                    duration?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = AppTheme.type.timelineDuration,
                            color = metadataColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                TimelineDetailsWithinHeight(
                    lines = details,
                    availableHeight = detailHeight,
                    textStyle = AppTheme.type.timelineMetadata,
                    revealProgress = detailProgress,
                    textColor = metadataColor,
                )
            }
        }
    }
}
/** A single instant. It deliberately has no card surface and never acquires duration geometry. */
@Composable
fun TimelinePointItem(
    title: String,
    trailing: String?,
    metadata: List<String>,
    zoom: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Optional resolved visible height. Without one, the text wraps to its natural height. */
    visualHeight: Dp? = null,
    completed: Boolean = false,
    overdue: Boolean = false,
    side: TimelinePointSide = TimelinePointSide.ACTUAL,
    trailingAction: (@Composable () -> Unit)? = null,
    prioritizeTitle: Boolean = false,
    showMarker: Boolean = true,
    markerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    alwaysVisibleMetadata: List<String> = emptyList(),
    revealMetadata: Boolean = timelineDetailProgress(zoom) > 0.45f,
    metadataProgress: Float = if (revealMetadata) 1f else 0f,
) {
    val alignment = if (side == TimelinePointSide.PLANNED) TextAlign.End else TextAlign.Start
    val titleWithMarker = buildAnnotatedString {
        if (overdue && !completed) withStyle(SpanStyle(color = AppTheme.colors.overdueMarker)) {
            append("! ")
        }
        append(title)
        if (completed) append("完成")
    }
    val text: @Composable ColumnScope.() -> Unit = {
        if (prioritizeTitle) Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Text(
                titleWithMarker,
                style = AppTheme.type.timelineTitle,
                overflow = TextOverflow.Clip,
                textAlign = alignment,
                modifier = Modifier.weight(1f),
            )
            trailing?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = AppTheme.type.timelineDuration,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
            Text(
                buildAnnotatedString {
                    if (overdue && !completed) withStyle(SpanStyle(color = AppTheme.colors.overdueMarker)) {
                        append("! ")
                    }
                    append(title)
                    trailing?.takeIf(String::isNotBlank)?.let {
                        append(" · ")
                        append(it)
                    }
                    if (completed) append("完成")
                },
                style = AppTheme.type.timelineTitle,
                overflow = TextOverflow.Clip,
                textAlign = alignment,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        alwaysVisibleMetadata.filter(String::isNotBlank).forEach { line ->
            Text(line, style = AppTheme.type.timelineMetadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = alignment, modifier = Modifier.fillMaxWidth())
        }
        val visibleMetadata = metadata.filter(String::isNotBlank)
        val progress = metadataProgress.coerceIn(0f, 1f)
        if (visibleMetadata.isNotEmpty() && progress > 0f) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx().toInt().coerceAtLeast(1) }
                val fullHeight = with(density) {
                    visibleMetadata.sumOf { line ->
                        measurer.measure(line, AppTheme.type.timelineMetadata,
                            constraints = Constraints(maxWidth = widthPx), softWrap = true,
                        ).size.height.toDouble()
                    }.toFloat().toDp()
                }
                Box(Modifier.fillMaxWidth().height(fullHeight * progress).clipToBounds()
                    .graphicsLayer { alpha = progress }) {
                    Column {
                        visibleMetadata.forEach { line ->
                            Text(line, style = AppTheme.type.timelineMetadata,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = alignment, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    // The transparent hit target may be taller than the content-driven visible row.
    Box(
        modifier
            .heightIn(min = AppTheme.dimens.minimumTouchTarget)
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (visualHeight == null) Modifier.wrapContentHeight()
                    else Modifier.height(visualHeight))
                .align(Alignment.TopStart)
                .clipToBounds(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        ) {
            if (showMarker && side == TimelinePointSide.ACTUAL) {
                Box(
                    Modifier
                        .size(AppTheme.dimens.spaceXs)
                        .background(markerColor, CircleShape)
                        .testTag("timeline_point_category_marker"),
                )
            }
            Column(Modifier.weight(1f), content = text)
            trailingAction?.invoke()
            if (showMarker && side == TimelinePointSide.PLANNED) {
                Box(
                    Modifier
                        .size(AppTheme.dimens.spaceXs)
                        .background(markerColor, CircleShape)
                        .testTag("timeline_point_category_marker"),
                )
            }
        }
    }
}

/** A clickable zero-duration fact. Only the axis mark is visible; the transparent target keeps
 * the exact time coordinate while preserving the minimum accessible touch size. */
@Composable
fun TimelinePointMarker(
    title: String,
    axisX: Dp,
    y: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.timelineAxis,
) {
    val touchSize = AppTheme.dimens.minimumTouchTarget
    Box(
        modifier
            .offset(x = axisX - touchSize / 2, y = y - touchSize / 2)
            .size(touchSize)
            .semantics { contentDescription = title }
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(AppTheme.dimens.spaceS)
                .height(AppTheme.dimens.border * 2)
                .background(color, CircleShape),
        )
    }
}

@Composable
internal fun CentralTimelineEndpoint(
    axisX: Dp,
    y: Dp,
    modifier: Modifier = Modifier,
    placement: TimelineBoundaryPlacement = TimelineBoundaryPlacement.CENTERED,
) {
    val dotSize = AppTheme.dimens.timelineDotSize
    Box(
        modifier
            .offset(
                x = axisX - dotSize / 2,
                y = timelineBoundaryElementTop(y, dotSize, placement),
            )
            .size(dotSize)
            .background(AppTheme.colors.timelineAxis, CircleShape),
    )
}

@Composable
fun CentralTimelineAxis(
    axisX: Dp,
    top: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .offset(x = axisX - AppTheme.dimens.border / 2, y = top)
            .width(AppTheme.dimens.border)
            .height(height)
            .background(AppTheme.colors.timelineAxis),
    )
}

@Composable
internal fun CentralTimelineLabel(
    label: String,
    axisX: Dp,
    y: Dp,
    modifier: Modifier = Modifier,
    show: Boolean = true,
    placement: TimelineBoundaryPlacement = TimelineBoundaryPlacement.CENTERED,
) {
    if (!show) return
    val labelHeight = timelineTimeLabelHeight()
    Box(
        modifier
            .offset(
                x = axisX - AppTheme.dimens.timelineAxisLabelWidth / 2,
                y = timelineBoundaryElementTop(y, labelHeight, placement),
            )
            .width(AppTheme.dimens.timelineAxisLabelWidth)
            .height(labelHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppTheme.type.timelineTime,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun CurrentTimeIndicator(
    axisX: Dp,
    y: Dp,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.nowIndicator,
) {
    Box(modifier.offset(y = y)) {
        Box(
            Modifier
                .offset(x = axisX)
                .fillMaxWidth()
                .height(AppTheme.dimens.border)
                .background(color),
        )
        Box(
            Modifier
                .offset(
                    x = axisX - AppTheme.dimens.timelineDotSize,
                    y = -AppTheme.dimens.timelineDotSize + AppTheme.dimens.border / 2,
                )
                .size(AppTheme.dimens.timelineDotSize * 2)
                .background(color, CircleShape),
        )
    }
}

/** Today and Todo & Done keep the now line behind cards and its dot above the axis. */
@Composable
fun CurrentTimeLine(axisX: Dp, y: Dp, modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.nowIndicator) {
    Box(modifier.offset(x = axisX, y = y).fillMaxWidth()
        .height(AppTheme.dimens.border).background(color))
}

@Composable
fun CurrentTimeDot(axisX: Dp, y: Dp, modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.nowIndicator) {
    Box(modifier.offset(
        x = axisX - AppTheme.dimens.timelineDotSize,
        y = y - AppTheme.dimens.timelineDotSize + AppTheme.dimens.border / 2,
    ).size(AppTheme.dimens.timelineDotSize * 2).background(color, CircleShape))
}
