package io.github.peanutpersimmon.rilo.presentation.editor.session

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.presentation.editor.EditorMode
import io.github.peanutpersimmon.rilo.presentation.editor.EditorOptionsUiModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class SessionEditorUiState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.CREATE,
    val id: Long? = null,
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val startReferenceMillis: Long? = null,
    val endReferenceMillis: Long? = null,
    val temporalType: SessionTemporalType = SessionTemporalType.POINT,
    val wasActiveAtLoad: Boolean = false,
    val requiresCompletedIntervalEnd: Boolean = false,
    val requiredIntervalEndLimitMillis: Long? = null,
    val requiredIntervalZoneId: ZoneId? = null,
    val startTimeConfirmed: Boolean = true,
    val categoryId: Long? = null,
    val sourceEventId: Long? = null,
    val sourceEventTitle: String? = null,
    val kind: SessionKind = SessionKind.ACTIVITY,
    val location: String = "",
    val note: String = "",
    val options: EditorOptionsUiModel = EditorOptionsUiModel(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = mode == EditorMode.EDIT &&
        temporalType == SessionTemporalType.INTERVAL && endDate == null && endTime == null
    val canSavePast: Boolean get() = title.isNotBlank() && startTimeConfirmed && endComplete &&
        hasValidRequiredEnd &&
        (kind == SessionKind.ACTIVITY || endDate != null) && !saving
    private val hasValidRequiredEnd: Boolean get() {
        if (!requiresCompletedIntervalEnd) return true
        val date = endDate ?: return false
        val time = endTime ?: return false
        val zone = requiredIntervalZoneId ?: return false
        val limit = requiredIntervalEndLimitMillis ?: return false
        val endMillis = runCatching { io.github.peanutpersimmon.rilo.domain.time.resolveLocalMinute(
            date, time, zone, endReferenceMillis) }.getOrNull() ?: return false
        val startMillis = runCatching { io.github.peanutpersimmon.rilo.domain.time.resolveLocalMinute(
            startDate, startTime, zone, startReferenceMillis) }.getOrNull() ?: return false
        return endMillis > startMillis && endMillis <= limit
    }
    val endComplete: Boolean get() = (endDate == null) == (endTime == null)
    val canSaveEdit: Boolean get() = title.isNotBlank() && startTimeConfirmed && endComplete && !saving
    val canStartNow: Boolean get() = mode == EditorMode.CREATE && title.isNotBlank() &&
        !requiresCompletedIntervalEnd && !saving

    /** The time fields determine historical shape; a running interval retains its loaded identity. */
    val saveTemporalType: SessionTemporalType get() = when {
        kind != SessionKind.ACTIVITY || isActive || requiresCompletedIntervalEnd || endDate != null -> SessionTemporalType.INTERVAL
        else -> SessionTemporalType.POINT
    }

    fun withEnd(date: LocalDate?, time: LocalTime?): SessionEditorUiState =
        if (date != null && time != null) copy(
            endDate = date, endTime = time, temporalType = SessionTemporalType.INTERVAL,
        ) else withoutEnd()

    fun withoutEnd(): SessionEditorUiState = copy(
        endDate = null, endTime = null,
        temporalType = if (wasActiveAtLoad || requiresCompletedIntervalEnd || kind != SessionKind.ACTIVITY) SessionTemporalType.INTERVAL
            else SessionTemporalType.POINT,
    )
}

internal fun SessionEditorUiState.reprojectTimeZone(
    fromZone: ZoneId,
    toZone: ZoneId,
): SessionEditorUiState {
    if (loading || fromZone == toZone) return this
    val startMillis = runCatching { io.github.peanutpersimmon.rilo.domain.time.resolveLocalMinute(
        startDate, startTime, fromZone, startReferenceMillis) }.getOrElse {
        return copy(errorMessage = it.message)
    }
    val start = java.time.Instant.ofEpochMilli(startMillis).atZone(toZone)
    val end = if (endDate != null && endTime != null) {
        val endMillis = runCatching { io.github.peanutpersimmon.rilo.domain.time.resolveLocalMinute(
            requireNotNull(endDate), requireNotNull(endTime), fromZone, endReferenceMillis) }.getOrElse {
            return copy(errorMessage = it.message)
        }
        java.time.Instant.ofEpochMilli(endMillis).atZone(toZone)
    } else {
        null
    }
    return copy(
        startDate = start.toLocalDate(),
        startReferenceMillis = start.toInstant().toEpochMilli(),
        endReferenceMillis = end?.toInstant()?.toEpochMilli(),
        startTime = start.toLocalTime().withSecond(0).withNano(0),
        endDate = end?.toLocalDate() ?: endDate,
        endTime = end?.toLocalTime()?.withSecond(0)?.withNano(0) ?: endTime,
        requiredIntervalZoneId = requiredIntervalZoneId?.let { toZone },
    )
}
