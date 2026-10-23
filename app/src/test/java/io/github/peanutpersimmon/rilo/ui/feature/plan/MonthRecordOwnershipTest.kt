package io.github.peanutpersimmon.rilo.ui.feature.plan

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.presentation.plan.MonthContent
import io.github.peanutpersimmon.rilo.presentation.plan.PlanUiState
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthRecordOwnershipTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 31)
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun pointBelongsOnlyToItsStartDateAndIntervalCanCrossDate() {
        val pointAtLastMillisecond = record(1, end - 1, null, SessionTemporalType.POINT)
        val nextMonthPoint = record(2, end, null, SessionTemporalType.POINT)
        val crossingInterval = record(3, end - 60_000, end + 60_000, SessionTemporalType.INTERVAL)
        val active = record(4, start, null, SessionTemporalType.INTERVAL)
        val records = listOf(pointAtLastMillisecond, nextMonthPoint, crossingInterval, active)
        assertEquals(listOf(1L, 3L), monthRecordsOnDate(records, day, zone).map { it.id })
        assertEquals(listOf(2L, 3L), monthRecordsOnDate(records, day.plusDays(1), zone).map { it.id })
    }

    @Test fun monthItemsKeepCategoryForEachSourceAndOmitUnassigned() {
        val event = Event(id = 2, title = "日程", dateEpochDay = day.toEpochDay(),
            categoryId = 12, createdAtMillis = start, updatedAtMillis = start)
        val record = record(3, start + 60_000, null, SessionTemporalType.POINT)
            .copy(categoryId = 13)
        val planState = PlanUiState(selectedDate = day, monthContent = MonthContent.PLAN,
            events = listOf(event), nowMillis = start)
        assertEquals(listOf(12L), monthSummaryItems(planState, day, zone).map { it.categoryId })
        val recordState = PlanUiState(selectedDate = day, monthContent = MonthContent.RECORD,
            records = listOf(record), nowMillis = start)
        assertEquals(listOf(13L), monthSummaryItems(recordState, day, zone).map { it.categoryId })
    }

    private fun record(id: Long, startAt: Long, endAt: Long?, type: SessionTemporalType) =
        Session(id = id, title = "record $id", temporalType = type, startAtMillis = startAt,
            endAtMillis = endAt, createdAtMillis = startAt, updatedAtMillis = startAt)
}
