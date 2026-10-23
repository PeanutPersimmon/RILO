package io.github.peanutpersimmon.rilo.domain.model

enum class TimelineEntryType {
    WAKE_POINT,
    SESSION_BLOCK,
    SESSION_POINT,
    EVENT_BLOCK,
    SLEEP_POINT,
}

enum class TimelineState {
    ACTUAL,
    EXPLICIT_ACTUAL,
    IMPLICIT_ACTUAL,
    PLANNED,
    AMBIGUOUS,
    BOUNDARY,
}

sealed interface TimelineEntry {
    val sourceId: Long?
    val startAtMillis: Long
    val endAtMillis: Long?
    val title: String
    val entryType: TimelineEntryType
    val state: TimelineState
    val categoryId: Long?
    val note: String?
    val location: String?
}

data class SessionBlock(
    override val sourceId: Long,
    override val startAtMillis: Long,
    override val endAtMillis: Long,
    override val title: String,
    override val categoryId: Long?,
    override val note: String?,
    override val location: String?,
    val kind: SessionKind,
) : TimelineEntry {
    override val entryType = TimelineEntryType.SESSION_BLOCK
    override val state = TimelineState.ACTUAL
}

data class SessionPoint(
    override val sourceId: Long,
    override val startAtMillis: Long,
    override val title: String,
    override val categoryId: Long?,
    override val note: String?,
    override val location: String?,
) : TimelineEntry {
    override val endAtMillis: Long? = null
    override val entryType = TimelineEntryType.SESSION_POINT
    override val state = TimelineState.ACTUAL
}

data class EventBlock(
    override val sourceId: Long,
    override val startAtMillis: Long,
    override val endAtMillis: Long?,
    override val title: String,
    override val state: TimelineState,
    override val categoryId: Long?,
    override val note: String?,
    override val location: String?,
) : TimelineEntry {
    override val entryType = TimelineEntryType.EVENT_BLOCK
}

data class WakePoint(
    override val startAtMillis: Long,
    override val sourceId: Long? = null,
) : TimelineEntry {
    override val endAtMillis: Long? = null
    override val title: String = "Wake"
    override val entryType = TimelineEntryType.WAKE_POINT
    override val state = TimelineState.BOUNDARY
    override val categoryId: Long? = null
    override val note: String? = null
    override val location: String? = null
}

data class SleepPoint(
    override val startAtMillis: Long,
    override val sourceId: Long? = null,
) : TimelineEntry {
    override val endAtMillis: Long? = null
    override val title: String = "Sleep"
    override val entryType = TimelineEntryType.SLEEP_POINT
    override val state = TimelineState.BOUNDARY
    override val categoryId: Long? = null
    override val note: String? = null
    override val location: String? = null
}

