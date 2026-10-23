package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import io.github.peanutpersimmon.rilo.domain.usecase.CategoryPresentation
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.TodayPlanProjector
import io.github.peanutpersimmon.rilo.domain.usecase.TodayPlanSource
import io.github.peanutpersimmon.rilo.domain.usecase.TimelineMetadataSources
import io.github.peanutpersimmon.rilo.ui.feature.today.isPlan
import io.github.peanutpersimmon.rilo.ui.feature.today.isTimedSpan
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class TodayUiMapperTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 9, 24)
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun dateOnlyEventIsOnlyUntimedPlanItem() {
        val event = Event(id = 3, title = "组会", dateEpochDay = date.toEpochDay(),
            categoryId = 12, createdAtMillis = start, updatedAtMillis = start)
        val plans = TodayPlanProjector.project(date, zone, start + 12 * 3_600_000L,
            listOf(event))
        assertEquals(listOf(3L), plans.map { it.sourceId })
        val labels = ClassificationLabels(mapOf(12L to CategoryPresentation("会议", "neutral", false)))
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        val summary = TodayUiMapper.map(date, window, emptyList(), null, labels, plans,
            TimelineDisplayMode.OVERVIEW, start + 12 * 3_600_000L, zone, Locale.CHINA).planSummary!!
        assertEquals(1, summary.items.size)
        assertEquals(TodayPlanSource.EVENT, summary.items.single().source)
        assertEquals("neutral", summary.items.single().categoryColorKey)
    }
    @Test fun dateLabelDerivesEnglishWeekdayFromActualDate() {
        assertEquals("20\nSEP · SUN", TodayUiMapper.formatDateLabel(LocalDate.of(2026, 9, 20)))
        assertEquals("10\nSEP · THU", TodayUiMapper.formatDateLabel(LocalDate.of(2026, 9, 10)))
    }

    @Test fun sessionPointMapsToActualSideWithoutDurationOrActiveState() {
        val time = start + 17 * 3_600_000 + 26 * 60_000
        val point = SessionPoint(42, time, "取快递", null, null, null)
        val active = Session(id = 42, title = "取快递",
            temporalType = SessionTemporalType.POINT, startAtMillis = time,
            endAtMillis = null, createdAtMillis = time, updatedAtMillis = time)
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)

        val entry = TodayUiMapper.map(date, window, listOf(point), active,
            ClassificationLabels(emptyMap()), emptyList(),
            TimelineDisplayMode.OVERVIEW, time, zone, Locale.CHINA).timelineEntries.single()

        assertEquals(TimelineEntryType.SESSION_POINT, entry.entryType)
        assertEquals(42L, entry.sourceId)
        assertEquals("17:26", entry.timeLabel)
        assertEquals(listOf(time), entry.axisTimes)
        assertNull(entry.endAtMillis)
        assertNull(entry.durationLabel)
        assertFalse(entry.isActive)
        assertFalse(entry.isPlan)
        assertFalse(entry.isTimedSpan)
    }

}
