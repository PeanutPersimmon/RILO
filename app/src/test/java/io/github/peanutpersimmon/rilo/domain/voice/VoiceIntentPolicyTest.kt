package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.service.SleepKindResolver
import io.github.peanutpersimmon.rilo.presentation.editor.EditorMode
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIntentPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 9, 26)
    private val now = at(15)
    private fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun draft(action: VoiceAction, fields: VoiceFields, instant: Long = now,
        eventType: VoiceEventTemporalType? = null, recordType: SessionTemporalType? = null) =
        VoiceActionDraft(action, "写论文", fields, "原句", instant,
            eventTemporalType = eventType, recordTemporalType = recordType)

    @Test fun policyDoesNotRetypeModelSelectedEvent() {
        val wrong = draft(VoiceAction.CreateEvent, VoiceFields(
            date = VoiceFieldPatch.Set(day), start = VoiceFieldPatch.Set(LocalTime.of(9, 0)),
            end = VoiceFieldPatch.Set(LocalTime.of(10, 0))),
            eventType = VoiceEventTemporalType.INTERVAL).copy(originalText = "刚才九点到十点开会了")
        val eventDecision = VoiceIntentPolicy.evaluate(wrong, zone) as VoiceIntentDecision.Ready
        assertEquals(VoiceAction.CreateEvent, eventDecision.draft.action)
        val reported = draft(VoiceAction.CreateRecord, VoiceFields(startAt = at(9), endAt = at(10)),
            recordType = SessionTemporalType.INTERVAL).copy(originalText = "刚才九点到十点开会了")
        assertTrue(VoiceIntentPolicy.evaluate(reported, zone) is VoiceIntentDecision.Ready)
    }

    @Test fun ambiguousSleepUsesExistingSleepCheckInPolicyAndExplicitNapWordsWin() {
        val shortDay = draft(VoiceAction.CreateSleepRecord,
            VoiceFields(startAt = at(13), endAt = at(14), sleepKind = SessionKind.NIGHT_SLEEP))
            .copy(originalText = "睡了一会")
        assertEquals(SessionKind.NAP, SleepKindResolver.resolve(shortDay.fields.sleepKind, at(13), at(14), zone,
            shortDay.originalText))
        assertEquals(SessionKind.NIGHT_SLEEP, SleepKindResolver.resolve(shortDay.fields.sleepKind, at(1), at(9), zone,
            shortDay.originalText))
        assertEquals(SessionKind.NAP, SleepKindResolver.resolve(shortDay.fields.sleepKind, at(13), at(14), zone,
            "午睡了"))
        val record = shortDay.copy(action = VoiceAction.CreateRecord, recordTemporalType = SessionTemporalType.INTERVAL)
        assertEquals(VoiceAction.CreateRecord, (VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready).draft.action)
    }

    @Test fun responsibilityAndDeadlineCannotBecomeEvent() {
        val event = draft(VoiceAction.CreateEvent, VoiceFields(
            title = VoiceFieldPatch.Set("买牙膏"),
            date = VoiceFieldPatch.Set(day.plusDays(1)),
            start = VoiceFieldPatch.Set(LocalTime.of(15, 0)),
        ), eventType = VoiceEventTemporalType.POINT)
        for (spoken in listOf("记得买牙膏", "周五前交论文")) {
            val result = VoiceIntentPolicy.evaluate(event.copy(originalText = spoken), zone)
            assertTrue(result is VoiceIntentDecision.NeedsClarification)
        }
        assertTrue(VoiceIntentPolicy.evaluate(event.copy(originalText = "明天下午三点开会"), zone)
            is VoiceIntentDecision.Ready)
    }
    @Test fun explicitTagRequestCannotBecomeCategoryOrAnEvent() {
        val event = draft(VoiceAction.CreateEvent, VoiceFields(
            title = VoiceFieldPatch.Set("开会"),
            date = VoiceFieldPatch.Set(day.plusDays(1)),
            start = VoiceFieldPatch.Set(LocalTime.of(15, 0)),
            categoryHint = "科研",
        ), eventType = VoiceEventTemporalType.POINT)
        for (spoken in listOf("明天下午三点开会，标签加科研", "明天下午三点开会，加 #论文", "给会议加 tag paper")) {
            val decision = VoiceIntentPolicy.evaluate(event.copy(originalText = spoken), zone)
            assertEquals(VoiceClarification.UnsupportedTag, (decision as VoiceIntentDecision.NeedsClarification).reason)
        }
        assertTrue(VoiceIntentPolicy.evaluate(event.copy(originalText = "明天下午三点开会，分类为科研"), zone)
            is VoiceIntentDecision.Ready)
    }
    @Test fun futureRecordIsClarifiedAndNeverBecomesEvent() {
        val record = draft(VoiceAction.CreateRecord, VoiceFields(startAt = at(16)),
            recordType = SessionTemporalType.POINT)
        val result = VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.InvalidFutureFact, result.reason)
        assertEquals(VoiceAction.CreateRecord, result.draft.action)
    }

    @Test fun completedIntervalRemainsRecordRegardlessOfClock() {
        val record = draft(VoiceAction.CreateRecord,
            VoiceFields(startAt = at(9), endAt = at(10)),
            recordType = SessionTemporalType.INTERVAL)
        val result = VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready
        assertEquals(VoiceAction.CreateRecord, result.draft.action)
        assertEquals(SessionTemporalType.INTERVAL, result.draft.recordTemporalType)
    }

    @Test fun pointRecordPersistsAsPointAndDoesNotCreateEvent() {
        val record = draft(VoiceAction.CreateRecord, VoiceFields(
            title = VoiceFieldPatch.Set("取快递"), startAt = at(14)),
            recordType = SessionTemporalType.POINT)
        val ready = (VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready).draft
        val prepared = VoiceExecutionPlanner.prepare(ready, zone = zone, now = now)
        assertTrue(prepared.canExecuteDirectly)
        assertEquals(SessionTemporalType.POINT, prepared.record!!.temporalType)
        assertNull(prepared.record!!.endAtMillis)
        assertNull(prepared.event)
    }

    @Test fun dateOnlyPastRecordPreservesDateAndRequestsActualTime() {
        val record = draft(VoiceAction.CreateRecord, VoiceFields(
            title = VoiceFieldPatch.Set("取快递"),
            date = VoiceFieldPatch.Set(day.minusDays(1))),
            recordType = SessionTemporalType.POINT)
        val result = VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.RecordMissingTime, result.reason)
        assertEquals(day.minusDays(1), result.draft.fields.date.applyTo(null))
        assertNull(result.draft.fields.startAt)
    }

    @Test fun sleepIsNeverChangedToEventByFutureTime() {
        val sleep = draft(VoiceAction.CreateSleepRecord,
            VoiceFields(startAt = at(16), endAt = at(17), sleepKind = SessionKind.NAP))
        val result = VoiceIntentPolicy.evaluate(sleep, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.InvalidFutureFact, result.reason)
        assertEquals(VoiceAction.CreateSleepRecord, result.draft.action)
    }

    @Test fun startRecordRetainsActiveInterval() {
        val record = draft(VoiceAction.StartRecord, VoiceFields(startAt = now))
        val ready = (VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready).draft
        val prepared = VoiceExecutionPlanner.prepare(ready, zone = zone, now = now)
        assertEquals(SessionTemporalType.INTERVAL, prepared.record!!.temporalType)
        assertNull(prepared.record!!.endAtMillis)
    }

    @Test fun explicitRecordUpdateCanChangePointAndIntervalShapes() {
        val point = Session(id = 7, title = "实验", temporalType = SessionTemporalType.POINT,
            startAtMillis = at(9), createdAtMillis = 0, updatedAtMillis = 0)
        val toInterval = draft(VoiceAction.UpdateRecord,
            VoiceFields(startAt = at(9), endAt = at(10)), recordType = SessionTemporalType.INTERVAL)
        val interval = VoiceExecutionPlanner.prepare(toInterval, record = point, zone = zone, now = now).record!!
        assertEquals(SessionTemporalType.INTERVAL, interval.temporalType)
        assertEquals(at(10), interval.endAtMillis)
        val toPoint = draft(VoiceAction.UpdateRecord, VoiceFields(startAt = at(11)),
            recordType = SessionTemporalType.POINT)
        val updatedPoint = VoiceExecutionPlanner.prepare(toPoint, record = interval, zone = zone, now = now)
            .record!!
        assertEquals(SessionTemporalType.POINT, updatedPoint.temporalType)
        assertNull(updatedPoint.endAtMillis)
        assertEquals(at(11), updatedPoint.startAtMillis)
    }

    @Test fun clearingCompletedIntervalEndCreatesPointButActiveUpdateStaysActive() {
        val completed = Session(id = 7, title = "实验", temporalType = SessionTemporalType.INTERVAL,
            startAtMillis = at(9), endAtMillis = at(10), createdAtMillis = 0, updatedAtMillis = 0)
        val clearEnd = draft(VoiceAction.UpdateRecord,
            VoiceFields(end = VoiceFieldPatch.Clear), recordType = SessionTemporalType.INTERVAL)
        val ready = (VoiceIntentPolicy.evaluate(clearEnd, zone) as VoiceIntentDecision.Ready).draft
        val point = VoiceExecutionPlanner.prepare(ready, record = completed, zone = zone, now = now).record!!
        assertEquals(SessionTemporalType.POINT, point.temporalType)
        assertEquals(at(9), point.startAtMillis)
        assertNull(point.endAtMillis)
        assertFalse(SessionEditorUiState(loading = false, mode = EditorMode.EDIT,
            temporalType = point.temporalType).isActive)

        val active = completed.copy(endAtMillis = null)
        val rename = draft(VoiceAction.UpdateRecord, VoiceFields(title = VoiceFieldPatch.Set("新标题")))
        val stillActive = VoiceExecutionPlanner.prepare(rename, record = active, zone = zone, now = now).record!!
        assertEquals(SessionTemporalType.INTERVAL, stillActive.temporalType)
        assertNull(stillActive.endAtMillis)
    }
}
