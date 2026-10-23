package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.service.ResolvedClassification
import io.github.peanutpersimmon.rilo.domain.service.SleepKindResolver
import io.github.peanutpersimmon.rilo.domain.time.withMinutePrecision
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class VoiceMissingField { Date, Start, EndDate, End }
data class VoicePrepared(
    val event: Event? = null, val record: Session? = null,
    val missing: Set<VoiceMissingField> = emptySet(), val error: String? = null,
    val futureRecord: Boolean = false,
    val clarification: VoiceClarification? = null,
) {
    val canExecuteDirectly: Boolean get() = missing.isEmpty() && error == null
}

/** Pure preparation shared by direct execution and fallback. Optional editor fields are not requirements. */
object VoiceExecutionPlanner {
    fun prepare(d: VoiceActionDraft, event: Event? = null,
        record: Session? = null,
        classification: ResolvedClassification? = null, zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()): VoicePrepared {
        val f = d.fields
        val missing = mutableSetOf<VoiceMissingField>()
        if (d.ambiguousStart) missing += VoiceMissingField.Start
        if (d.ambiguousEnd) missing += VoiceMissingField.End
        fun date(day: Long?) = day?.let(LocalDate::ofEpochDay)
        fun minute(value: Int?) = value?.let { LocalTime.MIN.plusMinutes(it.toLong()) }
        fun millis(day: LocalDate?, time: LocalTime?): Long? = if (day != null && time != null) day.atTime(time).atZone(zone).toInstant().toEpochMilli() else null
        when (d.action) {
            VoiceAction.StopRecord -> return VoicePrepared(record = record,
                error = when {
                    record == null || record.kind != SessionKind.ACTIVITY ||
                        record.temporalType != SessionTemporalType.INTERVAL || record.endAtMillis != null -> "没有找到进行中的普通记录"
                    else -> null
                })
            VoiceAction.FinishSleep -> {
                val end = f.endAt ?: d.nowMillis
                return VoicePrepared(record = record, error = when {
                    record == null || record.kind == SessionKind.ACTIVITY ||
                        record.temporalType != SessionTemporalType.INTERVAL || record.endAtMillis != null -> "没有正在进行的睡眠"
                    end <= record.startAtMillis || end > now -> "睡眠结束时间无效"
                    else -> null
                })
            }
            VoiceAction.CreateSleepRecord, VoiceAction.StartSleep -> {
                val range = VoiceIntentPolicy.temporalRange(d, zone)
                val running = d.action == VoiceAction.StartSleep
                val start = range.startAt
                val end = if (running) null else range.endAt
                if (start == null) missing += VoiceMissingField.Start
                if (!running && end == null) missing += VoiceMissingField.End
                val kind = SleepKindResolver.resolve(f.sleepKind, start, end, zone, d.originalText)
                if (start == null || !running && end == null) return VoicePrepared(missing = missing)
                val result = Session(
                    title = f.title.applyTo(null) ?: when (kind) {
                        SessionKind.NIGHT_SLEEP -> "夜间睡眠"
                        else -> "小憩"
                    },
                    kind = if (running) SleepKindResolver.activeStorageKind(kind)
                        else kind ?: return VoicePrepared(missing = missing, error = "请确认睡眠类型"),
                    startAtMillis = start,
                    endAtMillis = end,
                    categoryId = null,
                    note = f.note.applyTo(null),
                    location = f.location.applyTo(null),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    sourceEventId = null,
                ).withMinutePrecision()
                val future = result.startAtMillis > now || result.endAtMillis?.let { it > now } == true
                return VoicePrepared(
                    record = result,
                    missing = missing,
                    futureRecord = future,
                    error = when {
                        kind !in setOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP) -> "当前无法创建睡眠记录"
                        future -> "睡眠记录不能发生在未来"
                        missing.isEmpty() && DataValidator.validateActualSession(result, now) != null -> "睡眠时间无效，请修改"
                        else -> null
                    },
                )
            }
            VoiceAction.CreateEvent, VoiceAction.UpdateEvent -> {
                val base = event
                val day = f.date.applyTo(date(base?.dateEpochDay))
                if (day == null) missing += VoiceMissingField.Date
                val start = f.start.applyTo(minute(base?.startMinuteOfDay))
                val end = f.end.applyTo(minute(base?.endMinuteOfDay))
                if (d.action == VoiceAction.CreateEvent) when (d.eventTemporalType) {
                    VoiceEventTemporalType.DATE -> if (start != null || end != null)
                        return VoicePrepared(error = "日期日程不能包含时刻")
                    VoiceEventTemporalType.POINT -> {
                        if (start == null) missing += VoiceMissingField.Start
                        if (end != null) return VoicePrepared(error = "时点日程不能包含结束时刻")
                    }
                    VoiceEventTemporalType.INTERVAL -> {
                        if (start == null) missing += VoiceMissingField.Start
                        if (end == null) missing += VoiceMissingField.End
                    }
                    null -> return VoicePrepared(error = "日程缺少时间形态")
                }
                if (end != null && start == null) missing += VoiceMissingField.Start
                val shiftedEnd = base?.endDateEpochDay?.let { old ->
                    if (day != null && f.date is VoiceFieldPatch.Set) old + day.toEpochDay() - base.dateEpochDay else old
                }
                if (missing.isNotEmpty()) return VoicePrepared(missing = missing)
                val resolvedDay = requireNotNull(day)
                val result = (base ?: Event(title = d.targetText, dateEpochDay = resolvedDay.toEpochDay(), createdAtMillis = now, updatedAtMillis = now)).copy(
                    title = f.title.applyTo(base?.title ?: d.targetText).orEmpty(), dateEpochDay = resolvedDay.toEpochDay(),
                    startMinuteOfDay = start?.toSecondOfDay()?.div(60), endMinuteOfDay = end?.toSecondOfDay()?.div(60),
                    endDateEpochDay = if (end == null) null else f.endDate.applyTo(date(shiftedEnd))?.toEpochDay(),
                    startReminderOffsetMinutes = if (d.action == VoiceAction.UpdateEvent)
                        f.reminderUpdate.applyTo(base?.startReminderOffsetMinutes)
                        else f.reminderOffsetMinutes,
                    location = f.location.applyTo(base?.location), note = f.note.applyTo(base?.note),
                    categoryId = if (d.action == VoiceAction.UpdateEvent) when (f.categoryUpdate) {
                        VoiceFieldPatch.Keep -> base?.categoryId
                        VoiceFieldPatch.Clear -> null
                        is VoiceFieldPatch.Set -> classification?.categoryId
                    } else classification?.categoryId,
                    updatedAtMillis = now)
                return VoicePrepared(event = result, missing = missing,
                    error = if (missing.isEmpty() && DataValidator.validateEvent(result).isNotEmpty()) "日程结束时间不能早于开始时间" else null)
            }
            VoiceAction.CreateRecord, VoiceAction.UpdateRecord, VoiceAction.StartRecord,
            VoiceAction.CreateRecordFromEvent -> {
                val copy = if (d.action == VoiceAction.CreateRecordFromEvent) event else null
                val base = record
                val wasActive = base?.temporalType == SessionTemporalType.INTERVAL && base.endAtMillis == null
                val clearsCompletedEnd = d.action == VoiceAction.UpdateRecord &&
                    f.end == VoiceFieldPatch.Clear && base?.temporalType == SessionTemporalType.INTERVAL &&
                    base.endAtMillis != null
                val addsEnd = d.action == VoiceAction.UpdateRecord &&
                    (f.endAt != null || f.end is VoiceFieldPatch.Set)
                val shape = when {
                    d.action == VoiceAction.StartRecord -> SessionTemporalType.INTERVAL
                    clearsCompletedEnd -> SessionTemporalType.POINT
                    addsEnd -> SessionTemporalType.INTERVAL
                    else -> d.recordTemporalType ?: base?.temporalType
                } ?: return VoicePrepared(error = "记录缺少时间形态")
                val localDate = f.date.applyTo(base?.startAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                })
                val localTime = f.start.applyTo(base?.startAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
                })
                val start = if (d.action == VoiceAction.CreateRecordFromEvent) f.startAt
                    else f.startAt ?: millis(localDate, localTime) ?: base?.startAtMillis
                if (start == null) missing += VoiceMissingField.Start
                val explicitEndDay = f.endDate.applyTo(base?.endAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                }) ?: localDate
                val explicitEndTime = f.end.applyTo(base?.endAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
                })
                val running = d.action == VoiceAction.StartRecord ||
                    d.action == VoiceAction.UpdateRecord && wasActive && !addsEnd
                val end = if (shape == SessionTemporalType.POINT || running) null
                    else if (d.action == VoiceAction.CreateRecordFromEvent) f.endAt
                    else f.endAt ?: millis(explicitEndDay, explicitEndTime) ?: base?.endAtMillis
                if (shape == SessionTemporalType.INTERVAL && !running && end == null) missing += VoiceMissingField.End
                if (missing.isNotEmpty()) return VoicePrepared(missing = missing)
                val actualStart = requireNotNull(start)
                val result = (base ?: Session(
                    title = copy?.title ?: d.targetText, startAtMillis = actualStart,
                    createdAtMillis = now, updatedAtMillis = now,
                )).copy(
                    title = f.title.applyTo(base?.title ?: copy?.title ?: d.targetText).orEmpty(),
                    temporalType = shape, startAtMillis = actualStart, endAtMillis = end,
                    location = f.location.applyTo(base?.location ?: copy?.location),
                    note = f.note.applyTo(base?.note ?: copy?.note),
                    categoryId = if (d.action == VoiceAction.UpdateRecord) when (f.categoryUpdate) {
                        VoiceFieldPatch.Keep -> base?.categoryId
                        VoiceFieldPatch.Clear -> null
                        is VoiceFieldPatch.Set -> classification?.categoryId
                    } else classification?.categoryId ?: base?.categoryId ?: copy?.categoryId,
                    sourceEventId = copy?.id ?: base?.sourceEventId,
                    updatedAtMillis = now,
                ).withMinutePrecision()
                val future = result.startAtMillis > now || result.endAtMillis?.let { it > now } == true
                return VoicePrepared(
                    record = result,
                    futureRecord = future,
                    error = when {
                        future -> "记录不能发生在未来"
                        DataValidator.validateActualSession(result, now) != null -> "记录时间无效，请修改"
                        else -> null
                    },
                )
            }
            VoiceAction.Clarify -> return VoicePrepared(error = "请先澄清输入")
        }
    }
}
