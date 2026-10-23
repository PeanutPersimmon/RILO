package io.github.peanutpersimmon.rilo.domain.voice.agent

import io.github.peanutpersimmon.rilo.domain.voice.VoiceExecutionPlanner
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class QwenMappedVoiceSafetyTest {
    private val now = Instant.parse("2026-09-23T13:30:00Z")
    private val context = VoiceAgentContext(now, ZoneId.of("Asia/Shanghai"))
    private val mapper = QwenToolCallMapper()

    @Test fun futureAiRecordIsRejectedByExistingValidatorPath() {
        val draft = mapper.map("create_record", """{
            "title":"未来记录",
            "temporal_type":"INTERVAL",
            "start_at":"2026-09-24T10:00:00+08:00",
            "end_at":"2026-09-24T11:00:00+08:00"
        }""", "未来记录", context)
        val prepared = VoiceExecutionPlanner.prepare(draft, zone = context.zone, now = now.toEpochMilli())
        assertTrue(prepared.futureRecord)
        assertEquals("记录不能发生在未来", prepared.error)
    }

    @Test fun endNotAfterStartIsRejectedByMapper() {
        assertThrows(InvalidVoiceToolCall::class.java) { mapper.map("create_record", """{
            "title":"错误记录",
            "temporal_type":"INTERVAL",
            "start_at":"2026-09-23T20:00:00+08:00",
            "end_at":"2026-09-23T19:00:00+08:00"
        }""", "错误记录", context) }
    }
}
