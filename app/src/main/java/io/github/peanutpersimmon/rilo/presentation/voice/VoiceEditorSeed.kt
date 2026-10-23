package io.github.peanutpersimmon.rilo.presentation.voice

import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rilo.domain.service.ResolvedClassification
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorUiState
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorUiState
import io.github.peanutpersimmon.rilo.presentation.editor.toLocalDate
import io.github.peanutpersimmon.rilo.presentation.editor.toLocalTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A one-shot editor prefill, not a persisted command or a database entity. */
data class VoiceEditorSeed(
    val draft: VoiceActionDraft,
    val classification: ResolvedClassification? = null,
    val event: Event? = null,
)

fun VoiceEditorSeed.apply(state: EventEditorUiState): EventEditorUiState {
    val f = draft.fields
    val date = f.date.applyTo(state.date) ?: state.date
    val endDate = if (f.endDate == VoiceFieldPatch.Keep && state.endDate != null && f.date is VoiceFieldPatch.Set)
        state.endDate.plusDays(date.toEpochDay() - state.date.toEpochDay()) else f.endDate.applyTo(state.endDate)
    return state.copy(title = f.title.applyTo(state.title).orEmpty(), date = date,
        startTime = f.start.applyTo(state.startTime), endTime = f.end.applyTo(state.endTime),
        startReminderOffsetMinutes = f.reminderOffsetMinutes ?: state.startReminderOffsetMinutes,
        endDate = endDate, location = f.location.applyTo(state.location).orEmpty(),
        note = f.note.applyTo(state.note).orEmpty(),
        categoryId = classification?.categoryId ?: state.categoryId)
}

fun VoiceEditorSeed.apply(state: SessionEditorUiState, zone: ZoneId): SessionEditorUiState {
    val f = draft.fields
    val e = event
    fun minute(value: Int?) = value?.let { LocalTime.MIN.plusMinutes(it.toLong()) }
    val plannedStart = e?.startMinuteOfDay?.let { epochDayMinuteToMillis(e.dateEpochDay, it, zone) }
    val plannedEnd = e?.endMinuteOfDay?.let {
        epochDayMinuteToMillis(e.endDateEpochDay ?: e.dateEpochDay, it, zone)
    }
    val usableEnd = plannedEnd?.takeIf { plannedStart != null && it > plannedStart && it <= draft.nowMillis }
    val copied = if (e == null) state else state.copy(title = e.title, note = e.note.orEmpty(), location = e.location.orEmpty(),
        categoryId = e.categoryId, sourceEventId = e.id, sourceEventTitle = e.title,
        startDate = LocalDate.ofEpochDay(e.dateEpochDay), startTime = minute(e.startMinuteOfDay) ?: state.startTime,
        endDate = usableEnd?.toLocalDate(zone), endTime = usableEnd?.toLocalTime(zone))
    val startDate = f.startAt?.toLocalDate(zone) ?: f.date.applyTo(copied.startDate) ?: copied.startDate
    val startTime = f.startAt?.toLocalTime(zone) ?: f.start.applyTo(copied.startTime) ?: copied.startTime
    val endDate = f.endAt?.toLocalDate(zone) ?: f.endDate.applyTo(copied.endDate)
        ?: startDate.takeIf { f.end is VoiceFieldPatch.Set }
    val proposedEndTime = f.endAt?.toLocalTime(zone) ?: f.end.applyTo(copied.endTime)
    val pointRecord = draft.recordTemporalType == SessionTemporalType.POINT
    val finalEndDate = endDate.takeUnless { pointRecord }
    val finalEndTime = proposedEndTime.takeUnless { pointRecord }
    val temporalType = when {
        copied.kind != SessionKind.ACTIVITY || copied.wasActiveAtLoad -> SessionTemporalType.INTERVAL
        draft.recordTemporalType == SessionTemporalType.INTERVAL -> SessionTemporalType.INTERVAL
        e?.endMinuteOfDay != null && !pointRecord -> SessionTemporalType.INTERVAL
        finalEndDate != null && finalEndTime != null -> SessionTemporalType.INTERVAL
        else -> SessionTemporalType.POINT
    }
    val hasExplicitStart = f.startAt != null || f.start is VoiceFieldPatch.Set
    return copied.copy(title = f.title.applyTo(copied.title).orEmpty(),
        startDate = startDate, startTime = startTime,
        startTimeConfirmed = if (e != null) plannedStart?.let { it <= draft.nowMillis } == true || hasExplicitStart
            else copied.startTimeConfirmed,
        endDate = finalEndDate,
        endTime = finalEndTime,
        temporalType = temporalType,
        requiresCompletedIntervalEnd = draft.action == VoiceAction.CreateRecordFromEvent &&
            temporalType == SessionTemporalType.INTERVAL && finalEndDate == null,
        requiredIntervalEndLimitMillis = draft.nowMillis.takeIf {
            draft.action == VoiceAction.CreateRecordFromEvent && temporalType == SessionTemporalType.INTERVAL && finalEndDate == null
        },
        requiredIntervalZoneId = zone.takeIf {
            draft.action == VoiceAction.CreateRecordFromEvent && temporalType == SessionTemporalType.INTERVAL && finalEndDate == null
        },
        location = f.location.applyTo(copied.location).orEmpty(), note = f.note.applyTo(copied.note).orEmpty(),
        categoryId = classification?.categoryId ?: copied.categoryId)
}
