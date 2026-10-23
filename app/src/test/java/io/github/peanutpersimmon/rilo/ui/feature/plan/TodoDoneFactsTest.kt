package io.github.peanutpersimmon.rilo.ui.feature.plan

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TodoDoneFactsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 9, 26)
    private fun at(hour: Int, minute: Int = 0) =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun record(id: Long, kind: SessionKind, start: Long, end: Long?) = Session(
        id = id, title = "记录", kind = kind, startAtMillis = start, endAtMillis = end,
        temporalType = SessionTemporalType.INTERVAL, createdAtMillis = start, updatedAtMillis = start)

    @Test fun leftPlanItemIsEventWithItsOwnCategoryAndDetails() {
        val event = Event(id = 8, title = "组会", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 10 * 60, location = "会议室", note = "带材料",
            categoryId = 32, createdAtMillis = 0, updatedAtMillis = 0)
        val item = todoDoneEventItem(event, zone)
        assertTrue(item.event)
        assertEquals(32L, item.categoryId)
        assertEquals(at(10), item.atMillis)
        assertEquals(listOf("会议室", "带材料"), todoDoneDetails(item))
    }

    @Test fun rightFactsIncludeOrdinaryRecordAndNapButNotNightSleepOrActive() {
        val activity = record(1, SessionKind.ACTIVITY, at(9), at(10))
        val nap = record(2, SessionKind.NAP, at(14), at(14, 30))
        val night = record(3, SessionKind.NIGHT_SLEEP, at(0), at(8))
        val active = record(4, SessionKind.ACTIVITY, at(11), null)
        val point = record(5, SessionKind.ACTIVITY, at(12), null)
            .copy(temporalType = SessionTemporalType.POINT)
        assertEquals(listOf(activity, nap, point),
            visibleTodoDoneRecords(listOf(activity, nap, night, active, point),
                at(0), day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()))
    }

    @Test fun legacyRecordTaskIdIsNeverRenderedAsRelation() {
        val existing = record(1, SessionKind.ACTIVITY, at(9), at(10))
            .copy(location = "图书馆", note = "已完成")
        assertEquals(listOf("图书馆", "已完成"), todoDoneRecordDetails(existing))
    }

    @Test fun pointAndIntervalKeepSeparateAnchors() {
        val point = record(1, SessionKind.ACTIVITY, at(9), null)
            .copy(temporalType = SessionTemporalType.POINT)
        val interval = record(2, SessionKind.ACTIVITY, at(9), at(10))
        val items = listOf(point, interval).mapNotNull {
            todoDoneRecordInput(it, at(0), at(23), 32f, 12f)
        }
        assertEquals(listOf(null, at(10)), items.map { it.end })
        assertEquals(listOf(12f, 0f), items.map { it.anchorLead })
    }

    @Test fun recordClickKeepsSessionIdentity() {
        val opened = mutableListOf<Pair<Long, SessionKind>>()
        val nap = record(2, SessionKind.NAP, at(14), at(14, 30))
        dispatchRecordClick(nap) { id, kind -> opened += id to kind }
        assertEquals(listOf(2L to SessionKind.NAP), opened)
    }
}
