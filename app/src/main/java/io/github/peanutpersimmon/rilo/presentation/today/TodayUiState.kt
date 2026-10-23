package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.service.AwakeDisplayWindowResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Content(
        val dateLabel: String,
        val awakeDay: AwakeDayUiModel,
        val timelineEntries: List<TimelineEntryUiModel>,
        val activeSessionId: Long?,
        val planSummary: TodayPlanSummaryUiModel?,
        val displayMode: TimelineDisplayMode,
        val nowMillis: Long,
        val isToday: Boolean = false,
        val zoneId: ZoneId = ZoneId.systemDefault(),
    ) : TodayUiState

    data class Error(val message: String) : TodayUiState
}

/** A data-free first frame. The real snapshot replaces it as soon as Room responds. */
internal fun todayInitialContent(
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): TodayUiState.Content {
    val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val defaults = DefaultAwakeTimeConfig.Standard
    val window = AwakeDisplayWindowResolver.resolveToday(
        displayDate = date,
        nightSleeps = emptyList(),
        relevantContent = emptyList(),
        defaultWakeMinute = defaults.wakeMinute,
        defaultSleepMinute = defaults.sleepMinute,
        zoneId = zoneId,
        nowMillis = nowMillis,
    )
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    fun label(time: Long) = Instant.ofEpochMilli(time).atZone(zoneId).format(timeFormat)
    return TodayUiState.Content(
        dateLabel = TodayUiMapper.formatDateLabel(date),
        awakeDay = AwakeDayUiModel(
            startAtMillis = window.displayInterval.startMillis,
            endAtMillis = window.displayInterval.endMillis,
            startLabel = label(window.displayInterval.startMillis),
            endLabel = label(window.displayInterval.endMillis),
            startSource = window.startSource,
            endSource = window.endSource,
        ),
        timelineEntries = emptyList(),
        activeSessionId = null,
        planSummary = null,
        displayMode = TimelineDisplayMode.OVERVIEW,
        nowMillis = nowMillis,
        isToday = true,
        zoneId = zoneId,
    )
}

