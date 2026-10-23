package io.github.peanutpersimmon.rilo.domain.model

data class Event(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int? = null,
    val startReminderOffsetMinutes: Int? = null,
    val endDateEpochDay: Long? = null,
    val endMinuteOfDay: Int? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class Session(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val kind: SessionKind = SessionKind.ACTIVITY,
    val temporalType: SessionTemporalType = SessionTemporalType.INTERVAL,
    val startAtMillis: Long,
    val endAtMillis: Long? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sourceEventId: Long? = null,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
)

data class KeywordRule(
    val id: Long = 0,
    val keyword: String,
    val categoryId: Long? = null,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class ProcessedCommand(
    val commandId: String,
    val processedAtMillis: Long,
)
