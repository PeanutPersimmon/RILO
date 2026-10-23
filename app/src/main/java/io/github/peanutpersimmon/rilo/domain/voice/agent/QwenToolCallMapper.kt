package io.github.peanutpersimmon.rilo.domain.voice.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.voice.*
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

open class InvalidVoiceToolCall(message: String) : IllegalArgumentException(message)
class UnsupportedVoiceToolCall(name: String) : InvalidVoiceToolCall("不支持的工具：$name")

/** Strict, allow-listed conversion. Tool names never select methods dynamically. */
class QwenToolCallMapper(
    private val protocolDiagnostic: (String) -> Unit = {},
) {
    fun map(name: String, arguments: String, originalText: String, context: VoiceAgentContext): VoiceActionDraft {
        val json = try {
            JsonParser.parseString(arguments).takeIf { it.isJsonObject }?.asJsonObject
                ?: throw InvalidVoiceToolCall("工具参数必须是 JSON object")
        } catch (error: InvalidVoiceToolCall) {
            throw error
        } catch (_: Exception) {
            throw InvalidVoiceToolCall("工具参数不是有效 JSON")
        }
        val legacy = when (name) {
            "create_event", "update_event" -> setOf("start_time", "end_date", "end_time", "target_start_time")
            else -> emptySet()
        }
        if (json.keySet().any { it in legacy })
            throw InvalidVoiceToolCall("工具使用了旧时间字段")
        if (name == "update_event") {
            val targetDate = json.date("target_date")
            val targetStartAt = json.offsetDateTime("target_start_at", context.zone)
            if (targetDate != null && targetStartAt != null && targetDate != targetStartAt.toLocalDate())
                throw InvalidVoiceToolCall("目标日期和开始时间不一致")
        }
        val now = context.now.toEpochMilli()
        val sourceText = originalText.takeIf(String::isNotBlank) ?: json.optionalString("source_text").orEmpty()
        if (name in setOf("create_sleep_record", "start_sleep", "finish_sleep") && json.has("category_hint"))
            protocolDiagnostic("Sleep tool category_hint ignored")
        return when (name) {
            "create_event" -> {
                val title = stripLocation(json.string("title"), json.optionalString("location"))
                draft(VoiceAction.CreateEvent, title, sourceText, now,
                    eventFields(json, creating = true, zone = context.zone), semanticIntent = VoiceSemanticIntent.PlannedArrangement,
                    eventTemporalType = json.eventTemporalType())
            }
            "update_event" -> draft(VoiceAction.UpdateEvent, json.string("target_query"), sourceText, now,
                eventFields(json, creating = false, zone = context.zone),
                targetDate = json.date("target_date") ?: json.offsetDateTime("target_start_at", context.zone)?.toLocalDate(),
                targetStart = json.offsetDateTime("target_start_at", context.zone)?.toLocalTime())
            "create_record" -> {
                val title = stripLocation(json.string("title"), json.optionalString("location"))
                val shape = json.recordTemporalType(required = true)!!
                draft(VoiceAction.CreateRecord,
                    title, sourceText, now, recordFields(json, creating = true, shape = shape),
                    semanticIntent = if (shape == SessionTemporalType.POINT) VoiceSemanticIntent.ReportedPoint
                        else VoiceSemanticIntent.ReportedActivity, recordTemporalType = shape)
            }
            "create_sleep_record" -> {
                val kind = json.sleepKind()
                val location = json.optionalString("location")
                val title = stripLocation(json.optionalString("title") ?: kind?.defaultSleepTitle() ?: "睡眠", location)
                draft(VoiceAction.CreateSleepRecord, title, sourceText, now,
                    VoiceFields(
                        title = VoiceFieldPatch.Set(title),
                        note = json.stringPatch("note"),
                        location = patch(location, false),
                        startAt = json.instantMillis("start_at"),
                        endAt = json.instantMillis("end_at"),
                        sleepKind = kind,
                    ),
                    semanticIntent = VoiceSemanticIntent.SleepRecord)
            }
            "update_record" -> draft(VoiceAction.UpdateRecord, json.string("target_query"), sourceText, now,
                recordFields(json, creating = false, shape = json.recordTemporalType(required = false)),
                targetStartAt = json.instantMillis("target_start_at"), recordTemporalType = json.recordTemporalType(required = false))
            "start_record" -> {
                val title = stripLocation(json.string("title"), json.optionalString("location"))
                draft(VoiceAction.StartRecord, title, sourceText, now,
                    VoiceFields(title = VoiceFieldPatch.Set(title), note = json.stringPatch("note"),
                        location = json.stringPatch("location"), startAt = now,
                        categoryHint = json.categoryHint()),
                    semanticIntent = VoiceSemanticIntent.StartTimer)
            }
            "start_sleep" -> {
                val kind = json.sleepKind()
                val title = json.optionalString("title") ?: kind?.defaultSleepTitle() ?: "睡眠"
                draft(VoiceAction.StartSleep, title, sourceText, now,
                    VoiceFields(
                        title = VoiceFieldPatch.Set(title),
                        startAt = json.instantMillis("start_at") ?: now,
                        endAt = null,
                        sleepKind = kind,
                    ),
                    semanticIntent = VoiceSemanticIntent.StartSleep)
            }
            "finish_sleep" -> draft(VoiceAction.FinishSleep, "睡眠", sourceText, now,
                VoiceFields(endAt = json.instantMillis("end_at") ?: now),
                semanticIntent = VoiceSemanticIntent.FinishSleep)
            "stop_record" -> draft(
                VoiceAction.StopRecord,
                json.optionalString("target_query").orEmpty(), sourceText, now, VoiceFields(endAt = now))
            "create_record_from_event" -> {
                val target = json.string("event_query")
                val shape = json.recordTemporalType(required = true)!!
                draft(VoiceAction.CreateRecordFromEvent, target, sourceText, now,
                    VoiceFields(startAt = if (shape == SessionTemporalType.POINT) json.instantMillis("actual_at") else json.instantMillis("actual_start_at"),
                        endAt = json.instantMillis("actual_end_at")),
                    targetDate = json.date("event_date"), recordTemporalType = shape)
            }
            "request_clarification" -> clarificationDraft(json, sourceText, now, context)
            else -> throw UnsupportedVoiceToolCall(name)
        }
    }

    private fun eventFields(json: JsonObject, creating: Boolean, zone: ZoneId): VoiceFields {
        val clear = json.clearFields(if (creating) setOf("start", "end", "location", "note")
            else setOf("start", "end", "location", "note", "reminder", "category"))
        val startAt = json.offsetDateTime("start_at", zone)
        val endAt = json.offsetDateTime("end_at", zone)
        val date = json.date("date")
        if (date != null && startAt != null)
            throw InvalidVoiceToolCall("date 和 start_at 不能同时提供")
        val location = json.optionalString("location")
        val reminder = json.reminderOffset()
        val category = json.categoryHint()
        if ("reminder" in clear && reminder != null || "category" in clear && category != null)
            throw InvalidVoiceToolCall("不能同时设置和清除同一字段")
        return VoiceFields(
            title = cleanedTitle(json, creating, location),
            date = patch(startAt?.toLocalDate() ?: date, false),
            start = patch(startAt?.toLocalTime(), "start" in clear),
            endDate = patch(endAt?.toLocalDate(), "end" in clear),
            end = patch(endAt?.toLocalTime(), "end" in clear),
            location = patch(location, "location" in clear),
            note = patch(json.optionalString("note"), "note" in clear),
            categoryHint = if (creating) category else null,
            categoryUpdate = if (creating) VoiceFieldPatch.Keep else patch(category, "category" in clear),
            reminderOffsetMinutes = if (creating) reminder else null,
            reminderUpdate = if (creating) VoiceFieldPatch.Keep else patch(reminder, "reminder" in clear),
            reminderOffsetUnresolved = json.optionalBoolean("reminder_offset_unresolved") == true,
        )
    }

    private fun recordFields(json: JsonObject, creating: Boolean, shape: SessionTemporalType?): VoiceFields {
        val clear = json.clearFields(if (creating) setOf("end", "location", "note")
            else setOf("end", "location", "note", "category"))
        if (json.has("duration_minutes")) throw InvalidVoiceToolCall("记录必须提供实际时刻，不能仅凭时长推断")
        val occurredAt = json.instantMillis("occurred_at")
        val startAt = json.instantMillis("start_at")
        val endAt = json.instantMillis("end_at")
        if (occurredAt != null && (startAt != null || endAt != null))
            throw InvalidVoiceToolCall("occurred_at 与起止时间不能同时提供")
        if (shape == SessionTemporalType.POINT && (startAt != null || endAt != null || "end" in clear))
            throw InvalidVoiceToolCall("POINT 只能使用 occurred_at")
        if (shape == SessionTemporalType.INTERVAL && occurredAt != null)
            throw InvalidVoiceToolCall("INTERVAL 必须使用 start_at 和 end_at")
        if ("end" in clear && endAt != null)
            throw InvalidVoiceToolCall("end_at 和清空 end 不能同时提供")
        if (startAt != null && endAt != null && endAt <= startAt)
            throw InvalidVoiceToolCall("结束时间必须晚于开始时间")
        val location = json.optionalString("location")
        val category = json.categoryHint()
        if ("category" in clear && category != null)
            throw InvalidVoiceToolCall("不能同时设置和清除分类")
        return VoiceFields(
            title = cleanedTitle(json, creating, location),
            note = patch(json.optionalString("note"), "note" in clear),
            location = patch(location, "location" in clear),
            startAt = occurredAt ?: startAt,
            endAt = endAt,
            end = if ("end" in clear) VoiceFieldPatch.Clear else VoiceFieldPatch.Keep,
            categoryHint = if (creating) category else null,
            categoryUpdate = if (creating) VoiceFieldPatch.Keep else patch(category, "category" in clear),
        )
    }

    private fun draft(action: VoiceAction, target: String, original: String, now: Long,
        fields: VoiceFields = VoiceFields(),
        targetDate: LocalDate? = null, targetStart: LocalTime? = null, targetStartAt: Long? = null,
        semanticIntent: VoiceSemanticIntent = VoiceSemanticIntent.Unknown,
        eventTemporalType: VoiceEventTemporalType? = null,
        recordTemporalType: SessionTemporalType? = null) =
        VoiceActionDraft(action, target, fields, original, now,
            targetDate = targetDate, targetStart = targetStart, targetStartAt = targetStartAt,
            semanticIntent = semanticIntent, eventTemporalType = eventTemporalType,
            recordTemporalType = recordTemporalType)

    private fun JsonObject.eventTemporalType(): VoiceEventTemporalType = when (string("temporal_type")) {
        "DATE" -> VoiceEventTemporalType.DATE
        "POINT" -> VoiceEventTemporalType.POINT
        "INTERVAL" -> VoiceEventTemporalType.INTERVAL
        else -> throw InvalidVoiceToolCall("Event temporal_type 必须是 DATE、POINT 或 INTERVAL")
    }

    private fun JsonObject.recordTemporalType(required: Boolean): SessionTemporalType? =
        when (if (required) string("temporal_type") else optionalString("temporal_type")) {
            null -> null
            "POINT" -> SessionTemporalType.POINT
            "INTERVAL" -> SessionTemporalType.INTERVAL
            else -> throw InvalidVoiceToolCall("Record temporal_type 必须是 POINT 或 INTERVAL")
        }

    private fun clarificationDraft(json: JsonObject, source: String, now: Long,
        context: VoiceAgentContext): VoiceActionDraft {
        val reason = when (json.string("reason")) {
            "OBJECT_TYPE_AMBIGUOUS" -> VoiceClarification.ObjectTypeAmbiguous
            "RECORD_MISSING_TIME" -> VoiceClarification.RecordMissingTime
            "EVENT_MISSING_TIME" -> VoiceClarification.EventMissingTime
            "REMINDER_OFFSET_UNRESOLVED" -> VoiceClarification.ReminderOffsetUnresolved
            "INTERVAL_MISSING_BOUNDARY" -> VoiceClarification.IntervalMissingBoundary
            "TARGET_NOT_UNIQUE" -> VoiceClarification.TargetNotUnique
            "MULTIPLE_PRIMARY_OBJECTS" -> VoiceClarification.MultiplePrimaryObjects
            "TIME_SEMANTIC_CONFLICT" -> VoiceClarification.TimeSemanticConflict
            "AMBIGUOUS_PLAN_OR_FACT" -> VoiceClarification.ObjectTypeAmbiguous
            "SLEEP_MISSING_START" -> VoiceClarification.SleepMissingStart
            "SLEEP_MISSING_END" -> VoiceClarification.SleepMissingEnd
            "SLEEP_KIND_UNRESOLVED" -> VoiceClarification.SleepKindUnresolved
            "NO_ACTIVE_SLEEP" -> VoiceClarification.NoActiveSleep
            "UNSUPPORTED_RESPONSIBILITY" -> VoiceClarification.UnsupportedResponsibility
            "UNSUPPORTED_TAG" -> VoiceClarification.UnsupportedTag
            "INVALID_FUTURE_FACT" -> VoiceClarification.InvalidFutureFact
            "INVALID_TIME_RANGE" -> VoiceClarification.InvalidTimeRange
            "TARGET_NOT_FOUND" -> VoiceClarification.TargetNotFound
            "MULTIPLE_TARGETS" -> VoiceClarification.TargetNotUnique
            else -> throw InvalidVoiceToolCall("未知澄清原因")
        }
        val options = emptyList<VoiceClarificationOption>()
        val date = json.date("known_date")
        val title = json.optionalString("record_title") ?: source
        return VoiceActionDraft(
            action = when (reason) {
                VoiceClarification.RecordMissingTime -> VoiceAction.CreateRecord
                VoiceClarification.ReminderOffsetUnresolved -> VoiceAction.CreateEvent
                else -> VoiceAction.Clarify
            },
            targetText = title,
            fields = VoiceFields(title = VoiceFieldPatch.Set(title), date = date?.let { VoiceFieldPatch.Set(it) } ?: VoiceFieldPatch.Keep),
            originalText = source, nowMillis = now, clarificationReason = reason,
            clarificationMessage = json.optionalString("question"), clarificationOptions = options,
            recordTemporalType = if (reason == VoiceClarification.RecordMissingTime) SessionTemporalType.POINT else null,
            semanticIntent = when (reason) {
                VoiceClarification.RecordMissingTime -> VoiceSemanticIntent.ReportedPoint
                VoiceClarification.ReminderOffsetUnresolved -> VoiceSemanticIntent.PlannedArrangement
                else -> VoiceSemanticIntent.Unknown
            },
        )
    }

    private fun <T> patch(value: T?, clear: Boolean): VoiceFieldPatch<T> = when {
        clear -> VoiceFieldPatch.Clear
        value != null -> VoiceFieldPatch.Set(value)
        else -> VoiceFieldPatch.Keep
    }

    private fun JsonObject.stringPatch(name: String, required: Boolean = false): VoiceFieldPatch<String> =
        patch(if (required) string(name) else optionalString(name), false)

    /** prompt 已约束 title 不含地点；这里做确定性兜底清洗。 */
    private fun cleanedTitle(json: JsonObject, required: Boolean, location: String?): VoiceFieldPatch<String> =
        patch(if (required) stripLocation(json.string("title"), location)
        else json.optionalString("title")?.let { stripLocation(it, location) }, false)

    private fun stripLocation(title: String, location: String?): String {
        val loc = location?.trim().orEmpty()
        if (loc.isEmpty()) return title
        // 仅剥离句首的“在XX”“XX”或句尾的“XX”，避免误删标题中其他位置的同名字符
        return title
            .replace(Regex("^在?${Regex.escape(loc)}[，,、]?\\s*"), "")
            .replace(Regex("\\s*[，,、]?${Regex.escape(loc)}$"), "")
            .trim()
            .ifEmpty { title }
    }

    private fun JsonObject.string(name: String): String = optionalString(name)
        ?: throw InvalidVoiceToolCall("缺少必填参数：$name")

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString)
            throw InvalidVoiceToolCall("$name 必须是字符串")
        return value.asString.trim().takeIf { it.isNotEmpty() }
            ?: throw InvalidVoiceToolCall("$name 不能为空")
    }

    private fun JsonObject.categoryHint(): String? =
        optionalString("category_hint")?.take(MAX_CATEGORY_HINT_LENGTH)

    private fun JsonObject.optionalBoolean(name: String): Boolean? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean)
            throw InvalidVoiceToolCall("$name 必须是布尔值")
        return value.asBoolean
    }

    private fun JsonObject.sleepKind(): SessionKind? = when (optionalString("sleep_type")) {
        null, "unresolved" -> null
        "nap" -> SessionKind.NAP
        "night_sleep" -> SessionKind.NIGHT_SLEEP
        else -> throw InvalidVoiceToolCall("sleep_type 必须是 nap 或 night_sleep")
    }

    private fun JsonObject.reminderOffset(): Int? {
        val value = get("reminder_offset_minutes") ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber)
            throw InvalidVoiceToolCall("reminder_offset_minutes 必须是非负整数")
        val number = try { value.asInt } catch (_: Exception) {
            throw InvalidVoiceToolCall("reminder_offset_minutes 必须是非负整数")
        }
        if (number < 0 || value.asDouble != number.toDouble())
            throw InvalidVoiceToolCall("reminder_offset_minutes 必须是非负整数")
        return number
    }

    private fun SessionKind.defaultSleepTitle(): String = when (this) {
        SessionKind.NAP -> "小憩"
        SessionKind.NIGHT_SLEEP -> "夜间睡眠"
        SessionKind.ACTIVITY -> throw InvalidVoiceToolCall("无效的睡眠类型")
    }

    private fun JsonObject.date(name: String, required: Boolean = false): LocalDate? {
        val raw = if (required) string(name) else optionalString(name) ?: return null
        return try { LocalDate.parse(raw) } catch (_: DateTimeException) {
            throw InvalidVoiceToolCall("$name 必须使用 YYYY-MM-DD")
        }
    }

    private fun JsonObject.offsetDateTime(name: String, zone: ZoneId): ZonedDateTime? {
        val raw = optionalString(name) ?: return null
        return try {
            OffsetDateTime.parse(raw).also {
                if (it.second != 0 || it.nano != 0)
                    throw InvalidVoiceToolCall("$name 只支持整分钟时间")
            }.atZoneSameInstant(zone)
        } catch (error: InvalidVoiceToolCall) {
            throw error
        } catch (_: DateTimeException) {
            throw InvalidVoiceToolCall("$name 必须是带时区偏移的 ISO-8601 时间")
        }
    }

    private fun JsonObject.instantMillis(name: String): Long? {
        val raw = optionalString(name) ?: return null
        return try { OffsetDateTime.parse(raw).toInstant().toEpochMilli() } catch (_: DateTimeException) {
            throw InvalidVoiceToolCall("$name 必须是带时区偏移的 ISO-8601 时间")
        }
    }

    private fun JsonObject.clearFields(allowed: Set<String>): Set<String> {
        val value = get("clear_fields") ?: return emptySet()
        if (!value.isJsonArray) throw InvalidVoiceToolCall("clear_fields 必须是字符串数组")
        return value.asJsonArray.map {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString)
                throw InvalidVoiceToolCall("clear_fields 必须是字符串数组")
            it.asString
        }.toSet().also { fields ->
            val unknown = fields - allowed
            if (unknown.isNotEmpty()) throw InvalidVoiceToolCall("不支持清空字段：${unknown.joinToString()}")
        }
    }
}
