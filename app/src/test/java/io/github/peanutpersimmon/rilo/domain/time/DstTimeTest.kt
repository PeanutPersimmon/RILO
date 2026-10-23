package io.github.peanutpersimmon.rilo.domain.time

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.service.SleepCheckInPolicy
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.presentation.today.*
import io.github.peanutpersimmon.rilo.reminder.ReminderPlan
import io.github.peanutpersimmon.rilo.ui.components.*
import io.github.peanutpersimmon.rilo.ui.feature.plan.reviewIntervalOnDay
import io.github.peanutpersimmon.rilo.ui.feature.plan.reviewIntervalsByDate
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DstTimeTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun date(value: String) = LocalDate.parse(value)

    @Test fun ordinaryDayHas24UnchangedTicks() {
        for (zone in listOf(la, ZoneId.of("Asia/Shanghai"))) {
            val ticks = dayTimelineTicks(date("2026-02-02"), zone)
            assertEquals(24, ticks.size)
            ticks.forEachIndexed { hour, tick ->
                assertEquals("%02d".format(hour), tick.label)
                assertEquals(hour / 24f, tick.fraction, 0.00001f)
            }
        }
    }

    @Test fun springHas23ActualHoursAndNoInvented02() {
        for ((zone, day) in listOf(la to date("2026-03-08"), berlin to date("2026-03-29"))) {
            val ticks = dayTimelineTicks(day, zone)
            assertEquals(23, ticks.size)
            assertFalse(ticks.any { it.label == "02" })
            assertEquals("03", ticks[2].label)
            assertEquals(2 / 23f, ticks[2].fraction, 0.00001f)
            assertDayPositionsMatchTicks(day, zone)
        }
    }

    @Test fun autumnHas25HoursAndBothRepeatedInstants() {
        for ((zone, day, hour) in listOf(Triple(la, date("2026-11-01"), "01"),
            Triple(berlin, date("2026-10-25"), "02"))) {
            val ticks = dayTimelineTicks(day, zone)
            val repeated = ticks.filter { it.label.startsWith(hour) }
            assertEquals(25, ticks.size)
            assertEquals(2, repeated.size)
            assertNotEquals(repeated[0].label, repeated[1].label)
            assertEquals(3_600_000L, repeated[1].instantMillis - repeated[0].instantMillis)
            assertTrue(repeated[1].fraction > repeated[0].fraction)
            assertDayPositionsMatchTicks(day, zone)
        }
    }

    private fun assertDayPositionsMatchTicks(day: LocalDate, zone: ZoneId) {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val geometry = TimelineGeometry(start, end, 1000f)
        dayTimelineTicks(day, zone).forEach { tick ->
            assertEquals(tick.fraction * 1000, geometry.timeToY(tick.instantMillis), 0.001f)
            val session = Session(id = 4, title = "record", startAtMillis = tick.instantMillis,
                endAtMillis = tick.instantMillis + 1_800_000L, createdAtMillis = start, updatedAtMillis = start)
            val clipped = requireNotNull(reviewIntervalOnDay(session, day, zone))
            assertEquals(geometry.timeToY(tick.instantMillis),
                requireNotNull(geometry.position(clipped.first, clipped.second)).topPx, 0.001f)
        }
    }

    @Test fun gapAndUnresolvedOverlapAreRejected() {
        assertThrows(DateTimeException::class.java) {
            resolveLocalMinute(date("2026-03-08"), LocalTime.of(2, 30), la)
        }
        assertThrows(DateTimeException::class.java) {
            resolveLocalMinute(date("2026-11-01"), LocalTime.of(1, 30), la)
        }
        assertThrows(DateTimeException::class.java) {
            resolveLocalMinute(date("2026-03-29"), LocalTime.of(2, 30), berlin)
        }
    }

    @Test fun eachOverlapOffsetPreservesItsInstantAndMinuteIdentity() {
        val local = date("2026-11-01").atTime(1, 30)
        val instants = la.rules.getValidOffsets(local).map { local.toInstant(it).toEpochMilli() }
        assertEquals(2, instants.size)
        instants.forEach { assertEquals(it, resolveLocalMinute(local.toLocalDate(), local.toLocalTime(), la, it)) }
        assertNotEquals(timelineMinuteKey(instants[0], la), timelineMinuteKey(instants[1], la))
        assertEquals(instants, timelineMinuteLabelAnchors(instants, la))
        assertNotEquals(timelineClockLabel(instants[0], la), timelineClockLabel(instants[1], la))
    }

    @Test fun weekQueriesAndClippingRetainSevenDatesAcrossBothTransitions() {
        for (sunday in listOf(date("2026-03-08"), date("2026-11-01"))) {
            val dates = (6L downTo 0).map { sunday.minusDays(it) }
            val start = dates.first().atStartOfDay(la).toInstant().toEpochMilli()
            val end = sunday.plusDays(1).atStartOfDay(la).toInstant().toEpochMilli()
            val session = Session(id = 9, title = "cross midnight", startAtMillis = start,
                endAtMillis = end, createdAtMillis = start, updatedAtMillis = start)
            val indexed = reviewIntervalsByDate(listOf(session), dates, la)
            assertEquals(7, indexed.size)
            dates.forEach { assertEquals(listOf(session), indexed[it]) }
            val durations = dates.map { requireNotNull(reviewIntervalOnDay(session, it, la)).let { (a, b) -> b - a } }
            assertEquals(List(6) { 86_400_000L }, durations.take(6))
            assertEquals(if (sunday.monthValue == 3) 23 * 3_600_000L else 25 * 3_600_000L, durations.last())
            // Existing Week UI still has one fixed 24-hour gutter; the per-day scales differ.
            assertNotEquals(dayTimelineTicks(dates.first(), la).map { it.fraction },
                dayTimelineTicks(sunday, la).map { it.fraction })
        }
    }

    @Test fun sleepLimitUsesElapsedTimeAcrossDst() {
        for ((from, to, hours) in listOf(
            Triple("2026-03-08T00:30:00-08:00", "2026-03-08T10:30:00-07:00", 9L),
            Triple("2026-11-01T00:30:00-07:00", "2026-11-01T10:30:00-08:00", 11L))) {
            val a = OffsetDateTime.parse(from).toInstant().toEpochMilli()
            val b = OffsetDateTime.parse(to).toInstant().toEpochMilli()
            assertEquals(hours * 3_600_000, b - a)
            assertTrue(SleepCheckInPolicy.isAllowedOpenInterval(a, b))
        }
        val a = date("2026-11-01").atStartOfDay(la).toInstant().toEpochMilli()
        val b = date("2026-11-02").atStartOfDay(la).toInstant().toEpochMilli()
        assertTrue(SleepCheckInPolicy.isOpenIntervalTooLong(a, b))
        assertTrue(SleepCheckInPolicy.isAllowedOpenInterval(a, a + 86_400_000))
    }

    @Test fun halfOpenOverlapAndSelfExclusionRemainCorrect() {
        val first = OffsetDateTime.parse("2026-11-01T01:30:00-07:00").toInstant().toEpochMilli()
        val second = OffsetDateTime.parse("2026-11-01T01:30:00-08:00").toInstant().toEpochMilli()
        assertFalse(DataValidator.sessionsOverlap(first, second, second, second + 60_000))
        val session = Session(id = 7, title = "record", startAtMillis = first, endAtMillis = second,
            createdAtMillis = first, updatedAtMillis = second)
        assertFalse(DataValidator.overlapsAnySession(listOf(session), session.copy(title = "edited")))
    }

    @Test fun eventAndReminderUseWallMinuteNotElapsedMinutesAfterMidnight() {
        for ((zone, day) in listOf(la to date("2026-03-08"), la to date("2026-11-01"),
            berlin to date("2026-03-29"), berlin to date("2026-10-25"))) {
            val expected = day.atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
            assertEquals(expected, epochDayMinuteToMillis(day.toEpochDay(), 630, zone))
            val event = Event(id = 1, title = "event", dateEpochDay = day.toEpochDay(),
                startMinuteOfDay = 630, startReminderOffsetMinutes = 17,
                createdAtMillis = 0, updatedAtMillis = 0)
            val reminders = ReminderPlan.forEvent(event, zone)
            assertEquals(1, reminders.size)
            assertEquals(expected, reminders.single().anchorMillis)
            assertEquals(expected - 17 * 60_000, reminders.single().triggerMillis)
        }
    }

    @Test fun todayAcrossTransitionKeepsSessionInstantsAndElapsedDuration() {
        for ((startText, endText) in listOf(
            "2026-03-08T01:30:00-08:00" to "2026-03-08T03:30:00-07:00",
            "2026-11-01T01:30:00-07:00" to "2026-11-01T01:30:00-08:00")) {
            val start = OffsetDateTime.parse(startText).toInstant().toEpochMilli()
            val end = OffsetDateTime.parse(endText).toInstant().toEpochMilli()
            val day = Instant.ofEpochMilli(start).atZone(la).toLocalDate()
            val session = Session(id = 7, title = "record", startAtMillis = start, endAtMillis = end,
                createdAtMillis = start, updatedAtMillis = end)
            val snapshot = TodayStableSnapshot(day, emptyList(), TimelineDisplaySources(listOf(session), emptyList()),
                TimelineMetadataProjector.project(listOf(session), emptyList()), ClassificationLabels(emptyMap()))
            val content = projectTodaySnapshot(snapshot, null, day.atTime(6, 0).atZone(la).toInstant().toEpochMilli(),
                TimelineDisplayMode.OVERVIEW, DefaultAwakeTimeConfig(0, 23 * 60), la, Locale.ENGLISH) as TodayUiState.Content
            val entry = content.timelineEntries.single { it.sourceId == 7L }
            assertEquals(start, entry.startAtMillis)
            assertEquals(end, entry.endAtMillis)
            assertEquals("1h", entry.durationLabel)
            val geometry = TimelineGeometry(content.awakeDay.startAtMillis, content.awakeDay.endAtMillis, 1000f)
            assertTrue(geometry.timeToY(start) < geometry.timeToY(end))
            if (day.monthValue == 11) {
                assertTrue(entry.timeLabel.contains("-07:00"))
                assertTrue(entry.timeLabel.contains("-08:00"))
            }
        }
    }

    @Test fun legacyAmbiguousEventSchedulesOneReminderUsingExistingFirstOccurrence() {
        val day = date("2026-11-01")
        val event = Event(id = 1, title = "event", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 90, startReminderOffsetMinutes = 0, createdAtMillis = 0, updatedAtMillis = 0)
        val reminder = ReminderPlan.forEvent(event, la).single()
        assertEquals(OffsetDateTime.parse("2026-11-01T01:30:00-07:00").toInstant().toEpochMilli(), reminder.triggerMillis)
    }
}
