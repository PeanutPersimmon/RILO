package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.LocalTime

/** Transient values only. KEEP is distinct from an explicitly requested CLEAR. */
sealed interface VoiceFieldPatch<out T> {
    data object Keep : VoiceFieldPatch<Nothing>
    data class Set<T>(val value: T) : VoiceFieldPatch<T>
    data object Clear : VoiceFieldPatch<Nothing>
}

fun <T> VoiceFieldPatch<T>.applyTo(previous: T?): T? = when (this) {
    VoiceFieldPatch.Keep -> previous
    VoiceFieldPatch.Clear -> null
    is VoiceFieldPatch.Set -> value
}

enum class VoiceType { Event, Record }
enum class VoiceEventTemporalType { DATE, POINT, INTERVAL }
enum class VoiceSemanticIntent {
    PlannedArrangement,
    ReportedActivity,
    ReportedPoint,
    StartTimer,
    SleepRecord,
    StartSleep,
    FinishSleep,
    Unknown,
}
enum class VoiceAction {
    CreateEvent, UpdateEvent, CreateRecord, UpdateRecord,
    StartRecord, StopRecord, CreateRecordFromEvent, Clarify,
    CreateSleepRecord, StartSleep, FinishSleep,
}

const val MAX_CATEGORY_HINT_LENGTH = 64

data class VoiceFields(
    val title: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    val note: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    val date: VoiceFieldPatch<LocalDate> = VoiceFieldPatch.Keep,
    val start: VoiceFieldPatch<LocalTime> = VoiceFieldPatch.Keep,
    val endDate: VoiceFieldPatch<LocalDate> = VoiceFieldPatch.Keep,
    val end: VoiceFieldPatch<LocalTime> = VoiceFieldPatch.Keep,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val sleepKind: SessionKind? = null,
    val reminderOffsetMinutes: Int? = null,
    val reminderOffsetUnresolved: Boolean = false,
    val reminderUpdate: VoiceFieldPatch<Int> = VoiceFieldPatch.Keep,
    val location: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    /** Model-provided category phrase; never a database id. */
    val categoryHint: String? = null,
    val categoryUpdate: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
)

data class VoiceActionDraft(
    val action: VoiceAction,
    val targetText: String,
    val fields: VoiceFields,
    val originalText: String,
    val nowMillis: Long,
    val hints: List<String> = emptyList(),
    val needsExactTime: Boolean = false,
    val ambiguousStart: Boolean = false,
    val ambiguousEnd: Boolean = false,
    val targetDate: LocalDate? = null,
    val targetStart: LocalTime? = null,
    val targetStartAt: Long? = null,
    val semanticIntent: VoiceSemanticIntent = VoiceSemanticIntent.Unknown,
    val eventTemporalType: VoiceEventTemporalType? = null,
    val recordTemporalType: SessionTemporalType? = null,
    val clarificationReason: VoiceClarification? = null,
    val clarificationMessage: String? = null,
    val clarificationOptions: List<VoiceClarificationOption> = emptyList(),
)

data class VoiceClarificationOption(val type: VoiceType, val draft: VoiceActionDraft)
