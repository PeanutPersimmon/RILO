package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.BoundarySource
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.presentation.today.*
import io.github.peanutpersimmon.rilo.ui.components.*
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import io.github.peanutpersimmon.rilo.ui.theme.CategoryColors
import io.github.peanutpersimmon.rilo.ui.theme.resolveSleepVisualColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Excludes the moving endpoint and elapsed label of an active Session from bound search keys. */
internal fun todayZoomStructuralEntries(entries: List<TimelineEntryUiModel>): List<TimelineEntryUiModel> =
    entries.map { entry ->
        if (entry.isActive) entry.copy(endAtMillis = null, durationLabel = null,
            axisTimes = listOf(entry.startAtMillis)) else entry
    }

/** Only ordinary plan items and activity facts use Category color; sleep keeps its own visuals. */
internal fun todayCategoryColors(entry: TimelineEntryUiModel, colorScheme: ColorScheme): CategoryColors? {
    val ordinary = when (entry.entryType) {
        TimelineEntryType.EVENT_BLOCK,
        TimelineEntryType.SESSION_POINT -> true
        TimelineEntryType.SESSION_BLOCK -> entry.sessionKind == SessionKind.ACTIVITY
        TimelineEntryType.WAKE_POINT, TimelineEntryType.SLEEP_POINT -> false
    }
    return entry.categoryColorKey?.takeIf { ordinary }?.let { resolveCategoryColors(it, colorScheme) }
}

@Composable
fun TodayTimeline(
    awakeDay: AwakeDayUiModel,
    entries: List<TimelineEntryUiModel>,
    displayMode: TimelineDisplayMode,
    nowMillis: Long,
    onEntryClick: (TimelineEntryUiModel) -> Unit,
    modifier: Modifier = Modifier,
    onZoomGesture: (Boolean) -> Unit = {},
    isToday: Boolean = false,
    reminderVisible: Boolean = false,
    isLoadingShell: Boolean = false,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val density = LocalDensity.current
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    fun timeLabel(time: Long) = io.github.peanutpersimmon.rilo.domain.time.timelineClockLabel(time, zoneId, formatter)
    val scroll = rememberScrollState()
    val zoomCallback by rememberUpdatedState(onZoomGesture)
    val measurer = rememberTextMeasurer()
    val contentMeasurer = rememberTimelineContentMeasurer()
    val labelSpacing = measurer.measure(
        "00:00",
        AppTheme.type.timelineTime,
        maxLines = 1,
    ).size.height.toFloat() + with(density) { AppTheme.dimens.spaceXs.toPx() }
    BoxWithConstraints(modifier.clipToBounds()) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val axisX = maxWidth / 2
        val sideGap = AppTheme.dimens.timelineAxisGutter
        val sideWidth = (axisX - sideGap).coerceAtLeast(0.dp)
        val fullItemWidth = (sideWidth - AppTheme.dimens.spaceXs).coerceAtLeast(0.dp)
        val pointAnchorLead = maxOf(
            measurer.measure("Ag", AppTheme.type.timelineTitle, maxLines = 1).size.height,
            measurer.measure("2h", AppTheme.type.timelineDuration, maxLines = 1).size.height,
        ) / 2f
        val topBoundary = timelineTopBoundary(reminderVisible)
        val boundary = with(density) { topBoundary.toPx() }
        val denseContentTail = with(density) {
            AppTheme.dimens.timelineDenseContentTailSpace.toPx()
        }
        val itemWidthPx = with(density) { fullItemWidth.toPx().toInt().coerceAtLeast(1) }
        val itemGapPx = with(density) { AppTheme.dimens.timelineItemGap.toPx() }
        val blockInsetPx = with(density) { AppTheme.dimens.spaceXs.toPx() }
        fun entryDetails(entry: TimelineEntryUiModel): List<String> {
            return TimelineZoomPolicy.details(entry).filter(String::isNotBlank)
        }
        fun overviewDetails(entry: TimelineEntryUiModel): List<String> = emptyList()
        fun entryTitle(entry: TimelineEntryUiModel): String =
            if (entry.sessionKind == SessionKind.NAP) "☾ 小憩" else entry.title
        fun entryDuration(entry: TimelineEntryUiModel): String? =
            if (entry.isTimedSpan) entry.durationLabel ?:
                formatTimelineDuration(entry.startAtMillis, requireNotNull(entry.endAtMillis))
            else null
        // The clock may change an active interval's endpoint every second. It is intentionally
        // omitted from the bounds key; live geometry below still uses the latest endpoint.
        val boundsStructure = todayZoomStructuralEntries(entries)
        val measuredBoundsHeights = remember(boundsStructure,
            entries.map { if (it.isActive) it.durationLabel?.length else null }, itemWidthPx,
            contentMeasurer, measurer) {
            entries.map { entry ->
                contentMeasurer.height(entryTitle(entry), entryDuration(entry), overviewDetails(entry),
                    itemWidthPx, 1f, measurer, point = !entry.isTimedSpan) to
                    contentMeasurer.height(entryTitle(entry), entryDuration(entry),
                        overviewDetails(entry) + entryDetails(entry), itemWidthPx, 1f, measurer,
                        point = !entry.isTimedSpan)
            }
        }
        val overviewHeights = entries.indices.associate { it.let { index ->
            entries[index] to measuredBoundsHeights[index].first
        } }
        val fullHeights = entries.indices.associate { it.let { index ->
            entries[index] to measuredBoundsHeights[index].second
        } }
        val stableAxisEnd = awakeDay.endAtMillis.takeUnless {
            awakeDay.endSource == BoundarySource.NOW
        }
        val bounds = remember(boundsStructure, awakeDay.startAtMillis, stableAxisEnd,
            viewportHeightPx, itemWidthPx, boundary, denseContentTail, labelSpacing,
            pointAnchorLead, density, displayMode) {
            val overviewLayout = arrangeTimelineCards(
                awakeDay, entries, viewportHeightPx, itemGapPx, boundary, 1f,
                axisBottomInset = 0f, naturalCardHeight = 1f,
                timedBlockInset = blockInsetPx, labelSpacing = labelSpacing,
                contentHeight = overviewHeights::getValue, pointAnchorLead = pointAnchorLead,
            )
            val items = overviewLayout.rows.mapIndexed { index, row ->
                ElasticTimelineItem(
                    value = row.entry, start = row.start,
                    end = row.end.takeIf { row.entry.isTimedSpan }, side = row.entry.isPlan,
                    contentHeight = overviewHeights.getValue(row.entry),
                    stableOrder = row.entry.sourceId ?: index.toLong(),
                    anchorLead = if (row.entry.isTimedSpan) 0f else pointAnchorLead,
                )
            }
            calculateElasticTimelineZoomBounds(
                items = items,
                fullDetailHeights = fullHeights,
                axisStart = awakeDay.startAtMillis,
                axisEnd = awakeDay.endAtMillis,
                viewportHeight = viewportHeightPx,
                topBoundary = boundary,
                axisBottomInset = 0f,
                denseContentTail = denseContentTail,
                itemGap = itemGapPx,
                labelSpacing = labelSpacing,
                blockInset = blockInsetPx,
            )
        }
        val zoomDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        // The first real snapshot must start with its own fit bounds. Reusing the shell's
        // 1x zoom would draw one oversized Ready frame before LaunchedEffect corrects it.
        val zoom = remember(zoomDate, displayMode, isLoadingShell) {
            TimelineZoomState().apply {
                updateAdaptiveBounds(bounds.minZoom, bounds.maxZoom,
                    initialDetail = displayMode == TimelineDisplayMode.DETAIL)
            }
        }
        LaunchedEffect(zoom, bounds) {
            zoom.updateAdaptiveBounds(bounds.minZoom, bounds.maxZoom,
                initialDetail = displayMode == TimelineDisplayMode.DETAIL)
        }
        val measuredHeights = entries.associateWith { entry ->
            val overview = overviewHeights.getValue(entry)
            overview + (fullHeights.getValue(entry) - overview) * zoom.progress
        }
        val layout = arrangeTimelineCards(
            awakeDay,
            entries,
            viewportHeightPx,
            itemGapPx,
            boundary,
            zoom.scale,
            nowMillis.takeIf { isToday },
            axisBottomInset = 0f,
            naturalCardHeight = 1f,
            timedBlockInset = blockInsetPx,
            labelSpacing = labelSpacing,
            contentHeight = { entry -> measuredHeights.getValue(entry) },
            pointAnchorLead = pointAnchorLead,
            spanHeightLimit = { entry ->
                elasticIntervalHeightLimit(
                    overviewHeights.getValue(entry),
                    requireNotNull(entry.endAtMillis) - entry.startAtMillis, density.density,
                    (viewportHeightPx - boundary).coerceAtLeast(1f),
                    awakeDay.endAtMillis - awakeDay.startAtMillis,
                    bounds.minZoom, blockInsetPx,
                )
            },
            maxZoom = bounds.maxZoom,
        )
        val shownLabels = visibleTimelineLabels(layout, labelSpacing, zoneId,
            nowMillis.takeIf { isToday && awakeDay.endSource == BoundarySource.NOW })
        val axisStart = layout.axisStart
        val axisEnd = layout.axisEnd
        val visualAxisEndPx = axisEnd?.let(layout::timeY) ?: viewportHeightPx
        val scrollExtentPx = timelineScrollableExtent(
            layout.requiredHeight,
            visualAxisEndPx,
            denseContentTail,
        )
        val contentHeight = maxOf(maxHeight, with(density) { scrollExtentPx.toDp() })
        val scrollEnabled = scrollExtentPx > viewportHeightPx

        LaunchedEffect(scrollEnabled) {
            if (!scrollEnabled && scroll.value != 0) scroll.scrollTo(0)
        }

        Column(
            Modifier
                .fillMaxSize()
                .timelinePinchZoom(zoom, scroll, zoomCallback)
                .verticalScroll(scroll, enabled = scrollEnabled),
        ) {
            Box(Modifier.fillMaxWidth().height(contentHeight)) {
                layout.rows.forEach { row ->
                    val categoryColors = todayCategoryColors(row.entry, MaterialTheme.colorScheme)
                    val sleepColors = resolveSleepVisualColors(row.entry.sessionKind, MaterialTheme.colorScheme)
                    val sideStart = if (row.entry.isPlan) {
                        AppTheme.dimens.spaceXs
                    } else {
                        axisX + sideGap
                    }
                    val width = fullItemWidth
                    val top = with(density) { row.visualTop.toDp() }
                    val visualHeight = with(density) { row.visualHeight.toDp() }
                    val details = entryDetails(row.entry)
                    if (row.entry.isTimedSpan) {
                        TimelineBlockPlacement(
                            top,
                            visualHeight,
                            { onEntryClick(row.entry) },
                            Modifier.offset(x = sideStart).width(width).zIndex(TimelineCardLayer),
                            expandTouchTarget = false,
                        ) { visualModifier ->
                            TimelineTimeBlock(
                                title = if (row.entry.sessionKind == SessionKind.NAP) {
                                    "小憩"
                                } else {
                                    row.entry.title
                                },
                                duration = (row.entry.durationLabel
                                    ?: formatTimelineDuration(row.start, row.end)),
                                details = details,
                                kind = when {
                                    row.entry.sessionKind == SessionKind.NAP -> TimelineVisualKind.NAP
                                    row.entry.isPlan -> TimelineVisualKind.PLANNED
                                    else -> TimelineVisualKind.ACTUAL
                                },
                                modifier = visualModifier,
                                showCompactTitle = true,
                                accentColor = sleepColors?.base ?: categoryColors?.base?.takeIf { !row.entry.isPlan },
                                detailProgress = zoom.progress,
                                backgroundColor = sleepColors?.container ?: categoryColors?.container,
                                titleColor = sleepColors?.content ?: categoryColors?.content,
                                metadataColor = sleepColors?.secondaryContent ?: categoryColors?.secondaryContent,
                            )
                        }
                    } else {
                        TimelinePointItem(
                            title = row.entry.title,
                            trailing = null,
                            metadata = details,
                            zoom = zoom.scale,
                            onClick = { onEntryClick(row.entry) },
                            modifier = Modifier.offset(x = sideStart,
                                y = with(density) { row.visualTop.toDp() })
                                .width(width).zIndex(TimelinePointLayer),
                            visualHeight = with(density) { row.visualHeight.toDp() },
                            completed = false,
                            overdue = false,
                            side = if (row.entry.isPlan) TimelinePointSide.PLANNED else TimelinePointSide.ACTUAL,
                            prioritizeTitle = true,
                            showMarker = categoryColors != null,
                            markerColor = categoryColors?.base ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            alwaysVisibleMetadata = overviewDetails(row.entry),
                            metadataProgress = zoom.progress,
                        )
                    }
                }

                // The central gutter uses the same resolved anchors as the content.
                // The now line stays behind cards while its dot remains above the axis.
                if (axisStart != null && axisEnd != null) {
                    val axisTop = with(density) { layout.timeY(axisStart).toDp() }
                    val axisBottom = with(density) { layout.timeY(axisEnd).toDp() }
                    CentralTimelineAxis(
                        axisX,
                        axisTop,
                        axisBottom - axisTop,
                        Modifier.zIndex(TimelineAxisLayer),
                    )
                    if (isToday && nowMillis in axisStart..axisEnd) {
                        CurrentTimeLine(
                            axisX,
                            with(density) { layout.timeY(nowMillis).toDp() },
                            Modifier.zIndex(TimelineCardLayer - 0.1f),
                        )
                        CurrentTimeDot(
                            axisX,
                            with(density) { layout.timeY(nowMillis).toDp() },
                            Modifier.zIndex(TimelineNowLayer),
                        )
                    }
                    layout.axisTimes.forEach { time ->
                        CentralTimelineEndpoint(
                            axisX = axisX,
                            y = with(density) { layout.timeY(time).toDp() },
                            modifier = Modifier.zIndex(TimelineAxisLayer),
                            placement = if (time == axisEnd) {
                                TimelineBoundaryPlacement.INSIDE_BOTTOM
                            } else {
                                TimelineBoundaryPlacement.CENTERED
                            },
                        )
                    }
                    layout.axisTimes.forEach { time ->
                        CentralTimelineLabel(
                            label = timeLabel(time),
                            axisX = axisX,
                            y = with(density) { layout.timeY(time).toDp() },
                            modifier = Modifier.zIndex(TimelineAxisLayer),
                            show = time in shownLabels,
                            placement = if (time == axisEnd) {
                                TimelineBoundaryPlacement.INSIDE_BOTTOM
                            } else {
                                TimelineBoundaryPlacement.CENTERED
                            },
                        )
                    }
                }
            }
        }
    }
}
