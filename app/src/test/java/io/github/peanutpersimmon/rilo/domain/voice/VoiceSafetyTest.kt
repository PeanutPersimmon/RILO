package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rilo.presentation.editor.EditorMode
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorUiState
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorUiState
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rilo.presentation.voice.apply
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class VoiceSafetyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private fun parse(text: String): VoiceActionDraft {
        val command = when (text) {
            "论文截止改到下周一" -> "update_task" to """{"target_query":"论文","due_date":"2026-09-28"}"""
            "论文不要截止时间" -> "update_task" to """{"target_query":"论文","clear_fields":["due"]}"""
            "记录，写论文地点改为三楼" -> "update_record" to """{"target_query":"写论文","location":"三楼"}"""
            "组会改到周三" -> "update_event" to """{"target_query":"组会","date":"2026-09-23"}"""
            "把刚才的组会记成记录" -> "create_record_from_event" to """{"event_query":"组会","temporal_type":"INTERVAL","actual_start_at":"2026-09-22T14:00:00+08:00","actual_end_at":"2026-09-22T15:00:00+08:00"}"""
            "记录明天下午三点组会" -> "create_record" to """{"title":"组会","temporal_type":"POINT","occurred_at":"2026-09-23T15:00:00+08:00"}"""
            else -> error("No structured fixture for $text")
        }
        return QwenToolCallMapper().map(command.first, command.second, text, VoiceAgentContext(now, zone))
    }

    @Test fun multipleCandidatesNeverChooseAnIdEvenWithAnExactMatch() {
        val exact = VoiceTargetCandidate(1, VoiceType.Record, "写论文", "写论文")
        val other = VoiceTargetCandidate(2, VoiceType.Record, "写论文第一章", "写论文第一章")
        val candidates = listOf(exact, other).filter { VoiceTargetMatching.matches("写论文", it.title) }
        assertNull(VoiceTargetMatching.unique(candidates))
        assertEquals(exact, VoiceTargetMatching.unique(listOf(exact)))
        assertNull(VoiceTargetMatching.unique(emptyList()))
        assertFalse(VoiceTargetMatching.matches("", "写论文"))
    }
    @Test fun recordLocationPatchKeepsTimeTaskAndClassification() {
        val old = SessionEditorUiState(loading = false, mode = EditorMode.EDIT, id = 3, title = "写论文",
            temporalType = SessionTemporalType.INTERVAL,
            startDate = LocalDate.of(2026, 9, 21), startTime = LocalTime.of(14, 0),
            endDate = LocalDate.of(2026, 9, 21), endTime = LocalTime.of(15, 0),
            location = "一楼", categoryId = 5, note = "原笔记")
        val patched = VoiceEditorSeed(parse("记录，写论文地点改为三楼")).apply(old, zone)
        assertEquals(old.copy(location = "三楼"), patched)
    }
    @Test fun eventDateMovePreservesDurationAndMetadata() {
        val old = EventEditorUiState(title = "组会", date = LocalDate.of(2026, 9, 22),
            startTime = LocalTime.of(15, 0), endDate = LocalDate.of(2026, 9, 22), endTime = LocalTime.of(16, 0),
            categoryId = 5, location = "三楼")
        val patched = VoiceEditorSeed(parse("组会改到周三")).apply(old)
        assertEquals(old.copy(date = LocalDate.of(2026, 9, 23), endDate = LocalDate.of(2026, 9, 23)), patched)
    }
    @Test fun eventCopyRetainsSourceAndCanOverridePlannedTimes() {
        val event = Event(id = 9, title = "组会", dateEpochDay = LocalDate.of(2026, 9, 22).toEpochDay(),
            startMinuteOfDay = 14 * 60, endMinuteOfDay = 15 * 60, location = "三楼", note = "议程",
            categoryId = 4, createdAtMillis = 0, updatedAtMillis = 0)
        val tagged = event
        val draft = parse("把刚才的组会记成记录")
        val seed = VoiceEditorSeed(draft, event = tagged)
        val copied = seed.apply(SessionEditorUiState(), zone)
        assertEquals("组会", copied.title)
        assertEquals("三楼", copied.location)
        assertEquals(4L, copied.categoryId)
        assertEquals(LocalTime.of(14, 0), copied.startTime)
        assertEquals(LocalTime.of(15, 0), copied.endTime)
        assertEquals(9L, copied.sourceEventId)
        assertEquals("组会", copied.sourceEventTitle)
        val overridden = seed.copy(draft = draft.copy(fields = draft.fields.copy(
            startAt = LocalDate.of(2026, 9, 22).atTime(14, 15).atZone(zone).toInstant().toEpochMilli(),
            endAt = LocalDate.of(2026, 9, 22).atTime(15, 30).atZone(zone).toInstant().toEpochMilli())))
            .apply(SessionEditorUiState(), zone)
        assertEquals(LocalTime.of(14, 15), overridden.startTime)
        assertEquals(LocalTime.of(15, 30), overridden.endTime)
        assertEquals(14 * 60, event.startMinuteOfDay)
        assertEquals(15 * 60, event.endMinuteOfDay)
    }
    @Test fun futureRecordIsRejectedByTheSameValidatorAsRoom() {
        val d = parse("记录明天下午三点组会")
        val start = d.fields.startAt!!
        val session = Session(title = d.targetText, startAtMillis = start, createdAtMillis = now.toEpochMilli(), updatedAtMillis = now.toEpochMilli())
        assertEquals(DomainError.InvalidTime, DataValidator.validateActualSession(session, now.toEpochMilli()))
        assertEquals(DomainError.InvalidTime, DataValidator.validateActualSession(session.copy(startAtMillis = now.toEpochMilli() - 1000, endAtMillis = start), now.toEpochMilli()))
        assertNull(DataValidator.validateActualSession(session.copy(
            startAtMillis = now.toEpochMilli() - 60_000L,
            endAtMillis = now.toEpochMilli(),
        ), now.toEpochMilli()))
    }
    @Test fun pointRecordSeedKeepsPointEvenWhenAnOldEndIsPresent() {
        val at = LocalDate.of(2026, 9, 21).atTime(17, 26).atZone(zone).toInstant().toEpochMilli()
        val draft = VoiceActionDraft(VoiceAction.CreateRecord, "取快递",
            VoiceFields(title = VoiceFieldPatch.Set("取快递"), startAt = at),
            "刚刚取快递了", now.toEpochMilli(), recordTemporalType = SessionTemporalType.POINT)
        val old = SessionEditorUiState(endDate = LocalDate.of(2026, 9, 21),
            endTime = LocalTime.of(18, 0), temporalType = SessionTemporalType.INTERVAL)
        val applied = VoiceEditorSeed(draft).apply(old, zone)
        assertEquals(SessionTemporalType.POINT, applied.temporalType)
        assertEquals(LocalTime.of(17, 26), applied.startTime)
        assertNull(applied.endDate)
        assertNull(applied.endTime)
    }

    @Test fun eventIntervalWithFuturePlannedEndSeedsIncompleteEditorInterval() {
        val date = LocalDate.of(2026, 9, 22)
        val event = Event(id = 9, title = "组会", dateEpochDay = date.toEpochDay(),
            startMinuteOfDay = 14 * 60, endMinuteOfDay = 19 * 60,
            createdAtMillis = 0, updatedAtMillis = 0)
        val draft = VoiceActionDraft(VoiceAction.CreateRecordFromEvent, "组会", VoiceFields(),
            "记下组会", now.toEpochMilli(), recordTemporalType = SessionTemporalType.INTERVAL)
        val seeded = VoiceEditorSeed(draft, event = event).apply(SessionEditorUiState(), zone)
        assertEquals(SessionTemporalType.INTERVAL, seeded.temporalType)
        assertEquals(LocalTime.of(14, 0), seeded.startTime)
        assertNull(seeded.endDate)
        assertFalse(seeded.canSavePast)
        assertFalse(seeded.canStartNow)
        assertEquals(9L, seeded.sourceEventId)
    }
}
