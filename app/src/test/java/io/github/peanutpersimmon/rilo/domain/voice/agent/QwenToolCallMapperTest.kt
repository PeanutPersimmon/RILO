package io.github.peanutpersimmon.rilo.domain.voice.agent

import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenAgentProtocol
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorUiState
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rilo.presentation.voice.apply
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.reminderOffsetLabel
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class QwenToolCallMapperTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val context = VoiceAgentContext(Instant.parse("2026-09-23T13:30:00Z"), zone)
    private val mapper = QwenToolCallMapper()
    private fun map(name: String, json: String, text: String = "原始输入") =
        mapper.map(name, json, text, context)

    @Test fun toolSchemaExposesOnlyEventRecordSleepAndClarification() {
        val functions = QwenAgentProtocol.tools().map { it.asJsonObject.getAsJsonObject("function") }
        val names = functions.map { it.get("name").asString }.toSet()
        assertEquals(setOf("create_event", "update_event", "create_record", "create_sleep_record",
            "update_record", "start_record", "start_sleep", "finish_sleep", "stop_record", "create_record_from_event",
            "request_clarification"), names)
        assertFalse(QwenAgentProtocol.instructions(context).contains("create_task"))
        functions.forEach { function ->
            val properties = function.getAsJsonObject("parameters").getAsJsonObject("properties")
            assertTrue(properties.keySet().none { it in setOf("tagIds", "tagNames", "tags", "tag_ids", "tag_names") })
        }
    }

    @Test fun oldTaskToolsCannotMap() {
        for (name in listOf("create_task", "update_task", "complete_task", "reopen_task",
                "create_record_and_complete_task", "stop_record_and_complete_task")) {
            assertThrows(UnsupportedVoiceToolCall::class.java) { map(name, "{}") }
        }
    }

    @Test fun eventShapesAndExplicitReminderRemainAvailable() {
        val date = map("create_event", """{"title":"组会","temporal_type":"DATE","date":"2026-09-24"}""")
        val point = map("create_event", """{"title":"组会","temporal_type":"POINT",
            "start_at":"2026-09-24T15:00:00+08:00","reminder_offset_minutes":30}""",
            "明天下午三点开会")
        val interval = map("create_event", """{"title":"组会","temporal_type":"INTERVAL",
            "start_at":"2026-09-24T15:00:00+08:00","end_at":"2026-09-24T17:00:00+08:00"}""")
        assertEquals(VoiceEventTemporalType.DATE, date.eventTemporalType)
        assertEquals(VoiceEventTemporalType.POINT, point.eventTemporalType)
        assertEquals(30, point.fields.reminderOffsetMinutes)
        assertEquals(VoiceEventTemporalType.INTERVAL, interval.eventTemporalType)
        assertEquals(VoiceAction.CreateEvent, point.action)
    }

    @Test fun eventReminderAcceptsZeroAndArbitraryNonnegativeMinutesWithoutFallback() {
        val base = """{"title":"开会","temporal_type":"POINT","start_at":"2026-09-24T15:00:00+08:00"}"""
        val absent = map("create_event", base)
        assertNull(absent.fields.reminderOffsetMinutes)
        assertNull(VoiceExecutionPlanner.prepare(absent, zone = zone).event!!.startReminderOffsetMinutes)
        for (offset in listOf(0, 17, 45, 120, 150, 1440, 4320)) {
            val draft = map("create_event", "${base.dropLast(1)},\"reminder_offset_minutes\":$offset}")
            assertEquals(VoiceAction.CreateEvent, draft.action)
            assertEquals(offset, draft.fields.reminderOffsetMinutes)
            assertTrue(VoiceIntentPolicy.evaluate(draft, zone) is VoiceIntentDecision.Ready)
            assertEquals(offset, VoiceExecutionPlanner.prepare(draft, zone = zone).event!!.startReminderOffsetMinutes)
            if (offset in listOf(17, 150)) {
                assertEquals(offset, VoiceEditorSeed(draft).apply(EventEditorUiState()).startReminderOffsetMinutes)
                assertEquals("自定义 · $offset 分钟", reminderOffsetLabel(offset))
            }
        }
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("create_event", "${base.dropLast(1)},\"reminder_offset_minutes\":-1}")
        }
        val eventTool = QwenAgentProtocol.tools().map { it.asJsonObject.getAsJsonObject("function") }
            .single { it.get("name").asString == "create_event" }
        val schema = eventTool.getAsJsonObject("parameters")
        assertEquals(0, schema.getAsJsonObject("properties")
            .getAsJsonObject("reminder_offset_minutes").get("minimum").asInt)
        assertFalse(schema.getAsJsonArray("required").any { it.asString == "reminder_offset_minutes" })
    }

    @Test fun unspecifiedAdvanceReminderClarifiesWithoutChangingEventType() {
        val draft = map("request_clarification",
            """{"reason":"REMINDER_OFFSET_UNRESOLVED","question":"要提前多久提醒？"}""",
            "明天下午三点开会，提前提醒我")
        assertEquals(VoiceAction.CreateEvent, draft.action)
        assertEquals(VoiceClarification.ReminderOffsetUnresolved,
            (VoiceIntentPolicy.evaluate(draft, zone) as VoiceIntentDecision.NeedsClarification).reason)
        assertNull(draft.fields.reminderOffsetMinutes)
        assertTrue(QwenAgentProtocol.instructions(context).contains("REMINDER_OFFSET_UNRESOLVED"))
        val flagged = map("create_event", """{"title":"开会","temporal_type":"POINT",
            "start_at":"2026-09-24T15:00:00+08:00","reminder_offset_unresolved":true}""",
            "明天下午三点开会，提前提醒我")
        assertEquals(VoiceAction.CreateEvent, flagged.action)
        assertEquals(VoiceClarification.ReminderOffsetUnresolved,
            (VoiceIntentPolicy.evaluate(flagged, zone) as VoiceIntentDecision.NeedsClarification).reason)
        assertNull(flagged.fields.reminderOffsetMinutes)
    }

    @Test fun incompletePointEventKeepsEventIntentUntilTimeIsConfirmed() {
        val incomplete = map("create_event", """{"title":"开会","temporal_type":"POINT"}""",
            "明天下午开会")
        assertEquals(VoiceAction.CreateEvent, incomplete.action)
        assertEquals(VoiceClarification.EventMissingTime,
            (VoiceIntentPolicy.evaluate(incomplete, zone) as VoiceIntentDecision.NeedsClarification).reason)
        val prepared = VoiceExecutionPlanner.prepare(incomplete, zone = zone, now = context.now.toEpochMilli())
        assertEquals(setOf(VoiceMissingField.Date, VoiceMissingField.Start), prepared.missing)
        assertNull(prepared.event)
    }

    @Test fun createEventAlwaysRemainsEventEvenForReportedFactText() {
        val reported = map("create_event", """{"title":"开会","temporal_type":"INTERVAL",
            "start_at":"2026-09-23T15:00:00+08:00","end_at":"2026-09-23T16:00:00+08:00"}""",
            "刚才三点到四点开会了")
        assertEquals(VoiceAction.CreateEvent, reported.action)
        assertEquals(VoiceEventTemporalType.INTERVAL, reported.eventTemporalType)
    }

    @Test fun recordKeepsIntentWhenActualTimeIsIncompleteAndNeverAcceptsTaskQuery() {
        val incomplete = map("create_record", """{"title":"开会","temporal_type":"INTERVAL",
            "start_at":"2026-09-23T15:00:00+08:00"}""")
        assertEquals(VoiceAction.CreateRecord, incomplete.action)
        assertTrue(VoiceIntentPolicy.evaluate(incomplete, zone) is VoiceIntentDecision.NeedsClarification)
        val record = map("create_record", """{"title":"开会","temporal_type":"INTERVAL",
            "start_at":"2026-09-23T15:00:00+08:00","end_at":"2026-09-23T16:00:00+08:00"}""")
        assertEquals(SessionTemporalType.INTERVAL, record.recordTemporalType)
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("update_record", """{"target_query":"开会","clear_fields":["task"]}""")
        }
    }

    @Test fun sleepAndEventRecordToolsRemainAvailable() {
        assertEquals(VoiceAction.CreateSleepRecord, map("create_sleep_record",
            """{"sleep_type":"nap","start_at":"2026-09-23T14:00:00+08:00",
            "end_at":"2026-09-23T14:30:00+08:00"}""").action)
        val copied = map("create_record_from_event",
            """{"event_query":"组会","temporal_type":"POINT"}""")
        assertNull(copied.fields.startAt)
        assertEquals(VoiceAction.StopRecord, map("stop_record", "{}").action)
        assertEquals(VoiceAction.CreateSleepRecord, map("create_sleep_record", "{}", "睡了一会").action)
        assertNull(map("create_sleep_record", "{}", "睡了一会").fields.sleepKind)
        assertNull(map("create_sleep_record", """{"sleep_type":"unresolved"}""", "睡了一会").fields.sleepKind)
        assertEquals(VoiceAction.StartSleep, map("start_sleep", "{}", "我要睡了").action)
        assertEquals(VoiceAction.FinishSleep, map("finish_sleep", "{}", "刚睡醒").action)
    }

    @Test fun responsibilityClarificationHasNoTaskOption() {
        val draft = map("request_clarification",
            """{"reason":"OBJECT_TYPE_AMBIGUOUS","question":"请给出具体安排时间"}""",
            "记得买牙膏")
        assertEquals(VoiceAction.Clarify, draft.action)
        assertTrue(draft.clarificationOptions.isEmpty())
        val prompt = QwenAgentProtocol.instructions(context)
        assertTrue(prompt.contains("记得买牙膏"))
        assertTrue(prompt.contains("周五前交论文"))
    }

    @Test fun updateToolsExposeExplicitReminderAndCategoryPatches() {
        val functions = QwenAgentProtocol.tools().map { it.asJsonObject.getAsJsonObject("function") }
        fun props(name: String) = functions.single { it.get("name").asString == name }
            .getAsJsonObject("parameters").getAsJsonObject("properties")
        assertTrue(props("update_event").has("reminder_offset_minutes"))
        assertTrue(props("update_event").has("category_hint"))
        assertTrue(props("update_record").has("category_hint"))

        val keep = map("update_event", """{"target_query":"组会","title":"新组会"}""")
        assertEquals(VoiceFieldPatch.Keep, keep.fields.reminderUpdate)
        assertEquals(VoiceFieldPatch.Keep, keep.fields.categoryUpdate)
        val set = map("update_event", """{"target_query":"组会","reminder_offset_minutes":30,"category_hint":"工作"}""")
        assertEquals(VoiceFieldPatch.Set(30), set.fields.reminderUpdate)
        assertEquals(VoiceFieldPatch.Set("工作"), set.fields.categoryUpdate)
        assertEquals(VoiceFieldPatch.Set(0), map("update_event",
            """{"target_query":"组会","reminder_offset_minutes":0}""").fields.reminderUpdate)
        val clear = map("update_event", """{"target_query":"组会","clear_fields":["reminder","category"]}""")
        assertEquals(VoiceFieldPatch.Clear, clear.fields.reminderUpdate)
        assertEquals(VoiceFieldPatch.Clear, clear.fields.categoryUpdate)
        assertEquals(VoiceFieldPatch.Set("科研"), map("update_record",
            """{"target_query":"实验","category_hint":"科研"}""").fields.categoryUpdate)
        assertEquals(VoiceFieldPatch.Clear, map("update_record",
            """{"target_query":"实验","clear_fields":["category"]}""").fields.categoryUpdate)
        assertThrows(InvalidVoiceToolCall::class.java) { map("update_event",
            """{"target_query":"组会","reminder_offset_minutes":-1}""") }
        assertThrows(InvalidVoiceToolCall::class.java) { map("update_event",
            """{"target_query":"组会","reminder_offset_minutes":30,"clear_fields":["reminder"]}""") }
        assertThrows(InvalidVoiceToolCall::class.java) { map("update_record",
            """{"target_query":"实验","category_hint":"科研","clear_fields":["category"]}""") }
        val unresolved = map("update_event",
            """{"target_query":"组会","reminder_offset_unresolved":true}""",
            "把组会改为提前提醒")
        assertEquals(VoiceClarification.ReminderOffsetUnresolved,
            (VoiceIntentPolicy.evaluate(unresolved, zone) as VoiceIntentDecision.NeedsClarification).reason)
    }
}
