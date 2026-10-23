package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class VoiceClarification {
    ObjectTypeAmbiguous,
    RecordMissingTime,
    EventMissingTime,
    ReminderOffsetUnresolved,
    IntervalMissingBoundary,
    TargetNotUnique,
    MultiplePrimaryObjects,
    TimeSemanticConflict,
    SourceEventMissing,
    ManualTypeChange,
    UnsupportedTag,
    UnsupportedResponsibility,
    SleepMissingStart,
    SleepMissingEnd,
    SleepKindUnresolved,
    NoActiveSleep,
    InvalidFutureFact,
    InvalidTimeRange,
    TargetNotFound,
    CategoryUnresolved,
}

sealed interface VoiceIntentDecision {
    data class Ready(val draft: VoiceActionDraft) : VoiceIntentDecision
    data class NeedsClarification(
        val reason: VoiceClarification,
        val draft: VoiceActionDraft,
    ) : VoiceIntentDecision
}

data class VoiceTemporalRange(val startAt: Long?, val endAt: Long?)

/** Validates the model's chosen object and time shape; never changes its primary type. */
object VoiceIntentPolicy {
    fun evaluate(draft: VoiceActionDraft, zone: ZoneId): VoiceIntentDecision {
        draft.clarificationReason?.let { return clarify(it, draft) }
        val spoken = draft.originalText
        if (Regex("标签|(?i:\\btags?\\b)|#[\\p{L}]").containsMatchIn(spoken))
            return clarify(VoiceClarification.UnsupportedTag, draft)
        if (draft.action in setOf(VoiceAction.CreateEvent, VoiceAction.CreateRecord,
                VoiceAction.CreateSleepRecord, VoiceAction.StartRecord, VoiceAction.StartSleep) &&
            VoiceSemanticCue.isUnscheduledResponsibility(spoken))
            return clarify(VoiceClarification.UnsupportedResponsibility, draft)
        val f = draft.fields
        val range = temporalRange(draft, zone)
        return when (draft.action) {
            VoiceAction.Clarify -> clarify(VoiceClarification.ObjectTypeAmbiguous, draft)
            VoiceAction.CreateEvent -> {
                if (f.reminderOffsetUnresolved) return clarify(VoiceClarification.ReminderOffsetUnresolved, draft)
                val start = range.startAt
                val end = range.endAt
                when (draft.eventTemporalType) {
                    VoiceEventTemporalType.DATE ->
                        if (f.date.applyTo(null) != null && start == null && end == null)
                            VoiceIntentDecision.Ready(draft)
                        else clarify(if (f.date.applyTo(null) == null) VoiceClarification.EventMissingTime
                            else VoiceClarification.TimeSemanticConflict, draft)
                    VoiceEventTemporalType.POINT ->
                        if (start != null && end == null) VoiceIntentDecision.Ready(draft)
                        else clarify(if (start == null) VoiceClarification.EventMissingTime
                            else VoiceClarification.TimeSemanticConflict, draft)
                    VoiceEventTemporalType.INTERVAL ->
                        if (start == null || end == null) clarify(VoiceClarification.IntervalMissingBoundary, draft)
                        else if (end <= start) clarify(VoiceClarification.InvalidTimeRange, draft)
                        else VoiceIntentDecision.Ready(draft)
                    null -> clarify(VoiceClarification.TimeSemanticConflict, draft)
                }
            }
            VoiceAction.UpdateEvent -> if (f.reminderOffsetUnresolved)
                clarify(VoiceClarification.ReminderOffsetUnresolved, draft)
                else VoiceIntentDecision.Ready(draft)
            VoiceAction.CreateRecord -> {
                val start = range.startAt
                val end = range.endAt
                when (draft.recordTemporalType) {
                    SessionTemporalType.POINT ->
                        if (end != null || f.end != VoiceFieldPatch.Keep)
                            clarify(VoiceClarification.TimeSemanticConflict, draft)
                        else if (start == null) clarify(VoiceClarification.RecordMissingTime, draft)
                        else if (start > draft.nowMillis) clarify(VoiceClarification.InvalidFutureFact, draft)
                        else VoiceIntentDecision.Ready(draft)
                    SessionTemporalType.INTERVAL ->
                        if (start == null || end == null) clarify(VoiceClarification.IntervalMissingBoundary, draft)
                        else if (end <= start) clarify(VoiceClarification.InvalidTimeRange, draft)
                        else if (start > draft.nowMillis || end > draft.nowMillis)
                            clarify(VoiceClarification.InvalidFutureFact, draft)
                        else VoiceIntentDecision.Ready(draft)
                    null -> clarify(VoiceClarification.TimeSemanticConflict, draft)
                }
            }
            VoiceAction.CreateRecordFromEvent -> {
                if (range.startAt?.let { it > draft.nowMillis } == true ||
                    range.endAt?.let { it > draft.nowMillis } == true)
                    clarify(VoiceClarification.InvalidFutureFact, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.StartRecord -> {
                if (f.startAt == null || f.startAt > draft.nowMillis || f.endAt != null)
                    clarify(VoiceClarification.InvalidFutureFact, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.CreateSleepRecord, VoiceAction.StartSleep, VoiceAction.FinishSleep -> {
                if (range.startAt?.let { it > draft.nowMillis } == true ||
                    range.endAt?.let { it > draft.nowMillis } == true)
                    clarify(VoiceClarification.InvalidFutureFact, draft)
                else if (draft.action == VoiceAction.CreateSleepRecord &&
                    range.startAt != null && range.endAt != null && range.endAt <= range.startAt)
                    clarify(VoiceClarification.InvalidTimeRange, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.UpdateRecord -> {
                if (f.startAt?.let { it > draft.nowMillis } == true ||
                    f.endAt?.let { it > draft.nowMillis } == true)
                    clarify(VoiceClarification.TimeSemanticConflict, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            else -> VoiceIntentDecision.Ready(draft)
        }
    }

    private fun clarify(reason: VoiceClarification, draft: VoiceActionDraft) =
        VoiceIntentDecision.NeedsClarification(reason, draft)

    fun temporalRange(draft: VoiceActionDraft, zone: ZoneId): VoiceTemporalRange {
        val f = draft.fields
        val startDate = f.date.applyTo(null)
        val start = f.startAt ?: atMillis(startDate, f.start.applyTo(null), zone)
        val endDate = f.endDate.applyTo(null) ?: startDate
        val end = f.endAt ?: atMillis(endDate, f.end.applyTo(null), zone)
        return VoiceTemporalRange(start, end)
    }

    private fun atMillis(date: LocalDate?, time: LocalTime?, zone: ZoneId): Long? =
        if (date == null || time == null) null else date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

