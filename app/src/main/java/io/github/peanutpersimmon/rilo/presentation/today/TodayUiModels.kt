package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.BoundarySource
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.model.TimelineState

enum class TimelineDisplayMode { OVERVIEW, DETAIL }

data class TimelineEntryUiModel(
    val sourceId: Long?,
    val startAtMillis: Long,
    val endAtMillis: Long?,
    val title: String,
    val timeLabel: String,
    val durationLabel: String?,
    val entryType: TimelineEntryType,
    val state: TimelineState,
    val categoryLabel: String?,
    val location: String?,
    val note: String?,
    val isActive: Boolean,
    val categoryId: Long? = null,
    /** Real record endpoints only; excludes date-only projections, elapsed/estimate and awake boundaries. */
    val axisTimes: List<Long> = listOfNotNull(startAtMillis, endAtMillis),
    val completed: Boolean = false,
    val sourceEventTitle: String? = null,
    val sessionKind: io.github.peanutpersimmon.rilo.domain.model.SessionKind? = null,
    val categoryColorKey: String? = null,
)

data class TodayPlanItemUiModel(
    val sourceId: Long,
    val timeLabel: String?,
    val title: String,
    val source: io.github.peanutpersimmon.rilo.domain.usecase.TodayPlanSource = io.github.peanutpersimmon.rilo.domain.usecase.TodayPlanSource.EVENT,
    val completed: Boolean = false,
    val detail: String? = null,
    val categoryColorKey: String? = null,
)

data class TodayPlanSummaryUiModel(
    val remainingCount: Int,
    val nextTimeLabel: String?,
    val nextTitle: String,
    val items: List<TodayPlanItemUiModel>,
)

data class AwakeDayUiModel(
    val startAtMillis: Long,
    val endAtMillis: Long,
    val startLabel: String,
    val endLabel: String,
    val wakeAtMillis: Long? = null,
    val sleepAtMillis: Long? = null,
    val startSource: BoundarySource = if (wakeAtMillis == null) BoundarySource.DEFAULT else BoundarySource.REAL,
    val endSource: BoundarySource = if (sleepAtMillis == null) BoundarySource.DEFAULT else BoundarySource.REAL,
)
