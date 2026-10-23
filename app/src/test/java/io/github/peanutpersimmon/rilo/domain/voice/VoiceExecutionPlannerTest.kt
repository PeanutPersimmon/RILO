package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class VoiceExecutionPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 9, 22)
    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val event = Event(id = 9, title = "组会", dateEpochDay = day.toEpochDay(),
        startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60, createdAtMillis = 0, updatedAtMillis = 0)

    @Test fun eventPlanNeverSuppliesMissingActualTime() {
        val point = VoiceActionDraft(VoiceAction.CreateRecordFromEvent, "组会", VoiceFields(), "记下组会", at(18),
            recordTemporalType = SessionTemporalType.POINT)
        val pointResult = VoiceExecutionPlanner.prepare(point, event = event, zone = zone, now = at(18))
        assertEquals(setOf(VoiceMissingField.Start), pointResult.missing)
        assertNull(pointResult.record)

        val interval = point.copy(recordTemporalType = SessionTemporalType.INTERVAL)
        val intervalResult = VoiceExecutionPlanner.prepare(interval, event = event, zone = zone, now = at(18))
        assertEquals(setOf(VoiceMissingField.Start, VoiceMissingField.End), intervalResult.missing)
        assertNull(intervalResult.record)
    }

    @Test fun clearEndConvertsCompletedIntervalWhileOrdinaryActiveUpdatePreservesType() {
        val completed = Session(id = 7, title = "实验", temporalType = SessionTemporalType.INTERVAL,
            startAtMillis = at(9), endAtMillis = at(10), createdAtMillis = 0, updatedAtMillis = 0)
        val clear = VoiceActionDraft(VoiceAction.UpdateRecord, "实验",
            VoiceFields(end = VoiceFieldPatch.Clear), "清除结束时间", at(18))
        val point = VoiceExecutionPlanner.prepare(clear, record = completed, zone = zone, now = at(18)).record!!
        assertEquals(SessionTemporalType.POINT, point.temporalType)
        assertEquals(at(9), point.startAtMillis)
        assertNull(point.endAtMillis)

        val active = completed.copy(endAtMillis = null)
        val rename = clear.copy(fields = VoiceFields(title = VoiceFieldPatch.Set("实验二")))
        val preserved = VoiceExecutionPlanner.prepare(rename, record = active, zone = zone, now = at(18)).record!!
        assertEquals(SessionTemporalType.INTERVAL, preserved.temporalType)
        assertNull(preserved.endAtMillis)
    }
}
