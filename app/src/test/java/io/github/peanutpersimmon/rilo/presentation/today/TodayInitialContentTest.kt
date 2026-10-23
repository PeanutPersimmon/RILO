package io.github.peanutpersimmon.rilo.presentation.today

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayInitialContentTest {
    @Test fun firstFrameHasDateAndTimelineBeforeDatabaseSnapshot() {
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 10, 5)
        val now = date.atTime(10, 15).atZone(zone).toInstant().toEpochMilli()

        val content = todayInitialContent(now, zone, Locale.ENGLISH)

        assertEquals("5\nOCT · MON", content.dateLabel)
        assertEquals("07:00", content.awakeDay.startLabel)
        assertEquals("22:00", content.awakeDay.endLabel)
        assertTrue(content.timelineEntries.isEmpty())
        assertEquals(null, content.planSummary)
        assertTrue(content.isToday)
    }
}
