package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.AwakeDay
import io.github.peanutpersimmon.rilo.domain.model.AwakeDisplayWindow
import io.github.peanutpersimmon.rilo.domain.model.BoundarySource
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntry
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.TodayPlanItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TodayUiMapper {
    fun map(
        date: LocalDate,
        awakeDay: AwakeDay,
        entries: List<TimelineEntry>,
        activeSession: Session?,
        labels: ClassificationLabels,
        planItems: List<TodayPlanItem>,
        displayMode: TimelineDisplayMode,
        nowMillis: Long,
        zoneId: ZoneId,
        locale: Locale,
        metadataSources: io.github.peanutpersimmon.rilo.domain.usecase.TimelineMetadataSources? = null,
    ): TodayUiState.Content = map(
        date = date,
        displayWindow = AwakeDisplayWindow(
            anchorDate = awakeDay.calendarDate.value,
            displayInterval = awakeDay.interval,
            actualWakeAtMillis = awakeDay.wakeAtMillis,
            actualSleepAtMillis = awakeDay.sleepAtMillis,
            startSource = if (awakeDay.wakeAtMillis == null) BoundarySource.DEFAULT else BoundarySource.REAL,
            endSource = if (awakeDay.sleepAtMillis == null) BoundarySource.DEFAULT else BoundarySource.REAL,
            actualWakeSessionId = awakeDay.wakeSessionId,
            actualSleepSessionId = awakeDay.sleepSessionId,
        ),
        entries = entries,
        activeSession = activeSession,
        labels = labels,
        planItems = planItems,
        displayMode = displayMode,
        nowMillis = nowMillis,
        zoneId = zoneId,
        locale = locale,
        metadataSources = metadataSources,
    )

    fun map(
        date: LocalDate,
        displayWindow: AwakeDisplayWindow,
        entries: List<TimelineEntry>,
        activeSession: Session?,
        labels: ClassificationLabels,
        planItems: List<TodayPlanItem>,
        displayMode: TimelineDisplayMode,
        nowMillis: Long,
        zoneId: ZoneId,
        locale: Locale,
        metadataSources: io.github.peanutpersimmon.rilo.domain.usecase.TimelineMetadataSources? = null,
    ): TodayUiState.Content {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        val uiEntries = entries.map { entry ->
            val end = entry.endAtMillis
            val categoryPresentation = entry.categoryId?.let(labels.categoriesById::get)
            TimelineEntryUiModel(
                sourceId = entry.sourceId,
                startAtMillis = entry.startAtMillis,
                endAtMillis = end,
                title = entry.title,
                timeLabel = if (end == null) {
                    formatTime(entry.startAtMillis, zoneId, timeFormatter)
                } else {
                    "${formatTime(entry.startAtMillis, zoneId, timeFormatter)}–${formatTime(end, zoneId, timeFormatter)}"
                },
                durationLabel = end?.let { formatDuration(it - entry.startAtMillis) },
                entryType = entry.entryType,
                state = entry.state,
                sessionKind = (entry as? io.github.peanutpersimmon.rilo.domain.model.SessionBlock)?.kind,
                categoryId = entry.categoryId,
                categoryLabel = categoryPresentation?.name,
                categoryColorKey = categoryPresentation?.colorKey,
                location = entry.location,
                note = entry.note,
                isActive = entry.entryType == TimelineEntryType.SESSION_BLOCK &&
                    entry.sourceId == activeSession?.id,
            )
        }
        val plans = planItems.filter { it.startAtMillis == null }
        val planSummary = if (plans.isNotEmpty()) {
            TodayPlanSummaryUiModel(
                remainingCount = plans.count { !it.completed },
                nextTimeLabel = plans.firstOrNull()?.startAtMillis?.let {
                    val startLabel = formatTime(it, zoneId, timeFormatter)
                    plans.first().endAtMillis?.let { end ->
                        "$startLabel–${formatTime(end, zoneId, timeFormatter)}"
                    } ?: startLabel
                },
                nextTitle = plans.firstOrNull()?.title.orEmpty(),
                items = plans.map {
                    TodayPlanItemUiModel(
                        sourceId = it.sourceId,
                        timeLabel = it.startAtMillis?.let { start ->
                            val startLabel = formatTime(start, zoneId, timeFormatter)
                            it.endAtMillis?.let { end ->
                                "$startLabel–${formatTime(end, zoneId, timeFormatter)}"
                            } ?: startLabel
                        },
                        title = it.title, source = it.source, completed = it.completed,
                        detail = it.detail,
                        categoryColorKey = it.categoryId?.let(labels.categoriesById::get)?.colorKey,
                    )
                },
            )
        } else null
        return TodayUiState.Content(
            dateLabel = formatDateLabel(date),
            awakeDay = AwakeDayUiModel(
                startAtMillis = displayWindow.displayInterval.startMillis,
                endAtMillis = displayWindow.displayInterval.endMillis,
                startLabel = formatTime(displayWindow.displayInterval.startMillis, zoneId, timeFormatter),
                endLabel = formatTime(displayWindow.displayInterval.endMillis, zoneId, timeFormatter),
                wakeAtMillis = displayWindow.actualWakeAtMillis,
                sleepAtMillis = displayWindow.actualSleepAtMillis,
                startSource = displayWindow.startSource,
                endSource = displayWindow.endSource,
            ),
            timelineEntries = (metadataSources?.let {
                    TimelineEndpointMapper.map(uiEntries, it, labels, zoneId, date, nowMillis)
                } ?: uiEntries).map { entry ->
                    if (entry.entryType == TimelineEntryType.SESSION_BLOCK &&
                        entry.sourceId == activeSession?.id
                    ) {
                        entry.copy(
                            isActive = true,
                            durationLabel = formatDuration(nowMillis - entry.startAtMillis),
                        )
                    } else {
                        entry
                    }
                },
            activeSessionId = activeSession?.id,
            planSummary = planSummary,
            displayMode = displayMode,
            nowMillis = nowMillis,
            isToday = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate() == date,
            zoneId = zoneId,
        )
    }

    private fun formatTime(
        millis: Long,
        zoneId: ZoneId,
        formatter: DateTimeFormatter,
    ): String = io.github.peanutpersimmon.rilo.domain.time.timelineClockLabel(millis, zoneId, formatter)

    internal fun formatDuration(millis: Long): String {
        val totalMinutes = Duration.ofMillis(millis.coerceAtLeast(0)).toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    internal fun formatDateLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d\nMMM · EEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
}
