package io.github.peanutpersimmon.rilo.ui.feature.plan

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanReviewSleepTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 2)
    private fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun dayAndWeekDisplayCompletedSleepAndNap() {
        for (kind in listOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP)) {
            val sleep = Session(id = kind.ordinal.toLong() + 1, title = "睡眠", kind = kind,
                startAtMillis = at(14), endAtMillis = at(15),
                createdAtMillis = at(14), updatedAtMillis = at(15))
            assertEquals(at(14) to at(15), reviewIntervalOnDay(sleep, day, zone))
            assertEquals("14:00–15:00", reviewTimeLabel(sleep, day, zone))
        }
    }
}
