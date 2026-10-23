package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** These are structured tool fixtures. Live model tool selection requires device diagnostics. */
class VoicePhase5SentenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val context = VoiceAgentContext(Instant.parse("2026-10-15T12:00:00Z"), zone)
    private val mapper = QwenToolCallMapper()
    private fun map(text: String, tool: String, args: String) = mapper.map(tool, args, text, context)

    @Test fun recordAndEventFixturesKeepTheSelectedMainTypeAndShape() {
        val cases = listOf(
            Triple("刚才三点到四点开会了", "create_record", """{"title":"开会","temporal_type":"INTERVAL","start_at":"2026-10-15T15:00:00+08:00","end_at":"2026-10-15T16:00:00+08:00"}"""),
            Triple("刚刚吃完饭", "create_record", """{"title":"吃饭","temporal_type":"POINT","occurred_at":"2026-10-15T19:59:00+08:00"}"""),
            Triple("刚才出去买东西了", "create_record", """{"title":"买东西","temporal_type":"POINT"}"""),
            Triple("今天下午去了医院", "create_record", """{"title":"去医院","temporal_type":"POINT","occurred_at":"2026-10-15T15:00:00+08:00"}"""),
            Triple("明天体检", "create_event", """{"title":"体检","temporal_type":"DATE","date":"2026-10-16"}"""),
            Triple("明天下午三点开会", "create_event", """{"title":"开会","temporal_type":"POINT","start_at":"2026-10-16T15:00:00+08:00"}"""),
            Triple("明天下午三点到五点看电影", "create_event", """{"title":"看电影","temporal_type":"INTERVAL","start_at":"2026-10-16T15:00:00+08:00","end_at":"2026-10-16T17:00:00+08:00"}"""),
            Triple("今天下午去医院", "create_event", """{"title":"去医院","temporal_type":"DATE","date":"2026-10-15"}"""),
            Triple("周日去超市", "create_event", """{"title":"去超市","temporal_type":"DATE","date":"2026-10-18"}"""),
        )
        cases.forEachIndexed { index, (text, tool, args) ->
            val draft = map(text, tool, args)
            assertEquals(text, if (index < 4) VoiceAction.CreateRecord else VoiceAction.CreateEvent, draft.action)
            val decision = VoiceIntentPolicy.evaluate(draft, zone)
            if (index == 2) assertEquals(VoiceClarification.RecordMissingTime,
                (decision as VoiceIntentDecision.NeedsClarification).reason)
            else {
                assertTrue(text, decision is VoiceIntentDecision.Ready)
                val plan = VoiceExecutionPlanner.prepare(draft, zone = zone, now = context.now.toEpochMilli())
                assertTrue(text, plan.canExecuteDirectly)
                if (index < 4) assertNull(plan.event) else assertNull(plan.record)
            }
        }
        assertEquals(SessionTemporalType.INTERVAL, VoiceExecutionPlanner.prepare(
            map(cases[0].first, cases[0].second, cases[0].third), zone = zone,
            now = context.now.toEpochMilli()).record?.temporalType)
        val date = VoiceExecutionPlanner.prepare(map(cases[4].first, cases[4].second, cases[4].third),
            zone = zone, now = context.now.toEpochMilli()).event!!
        assertNull(date.startMinuteOfDay)
        assertNull(date.endMinuteOfDay)
    }

    @Test fun sleepFixturesPreserveSleepEvenWithoutKindOrTime() {
        val cases = listOf(
            Triple("睡觉", "{}", null),
            Triple("睡了一会", "{}", null),
            Triple("下午一点到两点睡了一会", """{"start_at":"2026-10-15T13:00:00+08:00","end_at":"2026-10-15T14:00:00+08:00"}""", SessionKind.NAP),
            Triple("午睡了一小时", "{}", null),
            Triple("我补了个觉", "{}", null),
            Triple("昨晚一点睡到今天九点", """{"start_at":"2026-10-15T01:00:00+08:00","end_at":"2026-10-15T09:00:00+08:00"}""", SessionKind.NIGHT_SLEEP),
        )
        cases.forEach { (text, args, expectedKind) ->
            val draft = map(text, "create_sleep_record", args)
            assertEquals(VoiceAction.CreateSleepRecord, draft.action)
            assertNull(draft.fields.sleepKind)
            assertTrue(VoiceIntentPolicy.evaluate(draft, zone) is VoiceIntentDecision.Ready)
            val plan = VoiceExecutionPlanner.prepare(draft, zone = zone, now = context.now.toEpochMilli())
            assertNull(plan.event)
            if (expectedKind == null) {
                assertFalse(plan.canExecuteDirectly)
                assertEquals(setOf(VoiceMissingField.Start, VoiceMissingField.End), plan.missing)
            } else {
                assertEquals(expectedKind, plan.record?.kind)
                assertTrue(plan.canExecuteDirectly)
            }
        }
        for (text in listOf("我要睡了", "现在睡觉")) {
            val draft = map(text, "start_sleep", "{}")
            val plan = VoiceExecutionPlanner.prepare(draft, zone = zone, now = context.now.toEpochMilli())
            assertEquals(VoiceAction.StartSleep, draft.action)
            assertEquals(SessionKind.NIGHT_SLEEP, plan.record?.kind)
            assertNull(plan.record?.endAtMillis)
        }
        for (text in listOf("刚睡醒", "我醒了"))
            assertEquals(VoiceAction.FinishSleep, map(text, "finish_sleep", "{}").action)
    }

    @Test fun ambiguityAndUnsupportedRequestsClarifyWithoutWritingAnotherType() {
        val cases = listOf(
            Triple("今天去医院", "AMBIGUOUS_PLAN_OR_FACT", VoiceClarification.ObjectTypeAmbiguous),
            Triple("记得买牙膏", "UNSUPPORTED_RESPONSIBILITY", VoiceClarification.UnsupportedResponsibility),
            Triple("周五前交论文", "UNSUPPORTED_RESPONSIBILITY", VoiceClarification.UnsupportedResponsibility),
            Triple("给它加标签论文", "UNSUPPORTED_TAG", VoiceClarification.UnsupportedTag),
        )
        cases.forEach { (text, reason, expected) ->
            val draft = map(text, "request_clarification", """{"reason":"$reason"}""")
            assertEquals(VoiceAction.Clarify, draft.action)
            assertEquals(expected, (VoiceIntentPolicy.evaluate(draft, zone) as VoiceIntentDecision.NeedsClarification).reason)
        }
        val wrongEvent = map("记得买牙膏", "create_event",
            """{"title":"买牙膏","temporal_type":"DATE","date":"2026-10-16"}""")
        assertEquals(VoiceClarification.UnsupportedResponsibility,
            (VoiceIntentPolicy.evaluate(wrongEvent, zone) as VoiceIntentDecision.NeedsClarification).reason)
        val wrongTag = map("给它加标签论文", "create_event",
            """{"title":"论文","temporal_type":"DATE","date":"2026-10-16","category_hint":"论文"}""")
        assertEquals(VoiceClarification.UnsupportedTag,
            (VoiceIntentPolicy.evaluate(wrongTag, zone) as VoiceIntentDecision.NeedsClarification).reason)
    }
}
