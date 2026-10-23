package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.AwakeDay
import io.github.peanutpersimmon.rilo.domain.model.CalendarDate
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntry
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.model.TimelineState
import io.github.peanutpersimmon.rilo.domain.service.TimelineProjector
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.CategoryPresentation
import io.github.peanutpersimmon.rilo.domain.usecase.TimelineMetadataProjector
import io.github.peanutpersimmon.rilo.ui.feature.today.todayCategoryColors
import io.github.peanutpersimmon.rilo.ui.feature.today.isTimedSpan
import io.github.peanutpersimmon.rilo.ui.feature.today.isPlan
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTimelineAssemblyTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 1, 1)
    private val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val awake = AwakeDay(
        calendarDate = CalendarDate(date),
        interval = TimeInterval(at(7), at(24)),
        wakeAtMillis = null,
        sleepAtMillis = null,
    )
    private val labels = ClassificationLabels(emptyMap())

    @Test
    fun `ordinary Today intervals and points retain category keys and theme colors`() {
        val categories = ClassificationLabels(
            categoriesById = mapOf(
                1L to CategoryPresentation("研究", "blue", false),
                2L to CategoryPresentation("会议", "neutral", false),
                3L to CategoryPresentation("旧分类", "retired", false),
            ),
        )
        val intervalEvent = Event(id = 2, title = "Event interval", categoryId = 2,
            dateEpochDay = date.toEpochDay(), startMinuteOfDay = 15 * 60,
            endMinuteOfDay = 16 * 60, createdAtMillis = dayStart, updatedAtMillis = dayStart)
        val pointEvent = Event(id = 3, title = "Event point", categoryId = 1,
            dateEpochDay = date.toEpochDay(), startMinuteOfDay = 17 * 60,
            createdAtMillis = dayStart, updatedAtMillis = dayStart)
        val intervalRecord = Session(id = 4, title = "Record interval", categoryId = 1,
            startAtMillis = at(8), endAtMillis = at(9),
            createdAtMillis = dayStart, updatedAtMillis = dayStart)
        val pointRecord = Session(id = 5, title = "Record point", categoryId = 3,
            temporalType = SessionTemporalType.POINT, startAtMillis = at(10),
            createdAtMillis = dayStart, updatedAtMillis = dayStart)
        val entries = assemble(sessions = listOf(intervalRecord, pointRecord),
            events = listOf(intervalEvent, pointEvent),
            classificationLabels = categories, nowMillis = at(18)).associateBy { it.sourceId }
        assertEquals("neutral", entries.getValue(2L).categoryColorKey)
        assertTrue(entries.getValue(2L).isTimedSpan)
        assertEquals("blue", entries.getValue(3L).categoryColorKey)
        assertFalse(entries.getValue(3L).isTimedSpan)
        assertEquals("blue", entries.getValue(4L).categoryColorKey)
        assertTrue(entries.getValue(4L).isTimedSpan)
        assertEquals("retired", entries.getValue(5L).categoryColorKey)
        assertFalse(entries.getValue(5L).isTimedSpan)

        val light = lightColorScheme()
        val dark = darkColorScheme()
        assertEquals(resolveCategoryColors("blue", light), todayCategoryColors(entries.getValue(4L), light))
        assertEquals(resolveCategoryColors("blue", dark), todayCategoryColors(entries.getValue(4L), dark))
        assertEquals(resolveCategoryColors("neutral", light), todayCategoryColors(entries.getValue(2L), light))
        assertEquals(resolveCategoryColors("neutral", light), todayCategoryColors(entries.getValue(5L), light))
        assertNull(todayCategoryColors(entries.getValue(4L).copy(categoryColorKey = null), light))
        assertNull(todayCategoryColors(entries.getValue(4L).copy(sessionKind = SessionKind.NAP), light))
        assertNull(todayCategoryColors(entries.getValue(4L).copy(sessionKind = SessionKind.NIGHT_SLEEP), light))
        assertEquals(listOf(at(8), at(9)), entries.getValue(4L).axisTimes)
        assertEquals(listOf(at(10)), entries.getValue(5L).axisTimes)
    }

    @Test
    fun `categorized nap keeps its special visuals and night sleep stays outside content`() {
        val labels = ClassificationLabels(mapOf(1L to CategoryPresentation("休息", "blue", false)))
        val nap = Session(id = 30, title = "小憩", kind = SessionKind.NAP,
            categoryId = 1, startAtMillis = at(13), endAtMillis = at(13, 30),
            createdAtMillis = dayStart, updatedAtMillis = dayStart)
        val sleep = Session(id = 31, title = "睡眠", kind = SessionKind.NIGHT_SLEEP,
            categoryId = 1, startAtMillis = at(21), endAtMillis = at(22),
            createdAtMillis = dayStart, updatedAtMillis = dayStart)
        val entries = assemble(sessions = listOf(nap, sleep), classificationLabels = labels,
            nowMillis = at(23))
        val napEntry = entries.single { it.sourceId == 30L }
        assertEquals(SessionKind.NAP, napEntry.sessionKind)
        assertNull(todayCategoryColors(napEntry, lightColorScheme()))
        assertTrue(entries.none { it.sourceId == 31L })
    }

    @Test
    fun `active session keeps projector effective end through final Today assembly`() {
        val active = Session(
                id = 1,
                title = "Active session",
                startAtMillis = at(14),
                endAtMillis = null,
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            )
        val asOf = at(15, 20)

        val entry = assemble(
            sessions = listOf(active),
            activeSession = active,
            nowMillis = asOf,
        ).single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(14), entry.startAtMillis)
        assertEquals(asOf, entry.endAtMillis)
        assertTrue(entry.isTimedSpan)
        assertTrue(entry.isActive)
        assertNull(active.endAtMillis)
    }

    @Test
    fun `finished session keeps persisted end instead of asOf`() {
        val finished = Session(
                id = 2,
                title = "Finished session",
                startAtMillis = at(14),
                endAtMillis = at(15),
                createdAtMillis = dayStart,
                updatedAtMillis = at(15),
            )

        val entry = assemble(
            sessions = listOf(finished),
            nowMillis = at(16),
        ).single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(14), entry.startAtMillis)
        assertEquals(at(15), entry.endAtMillis)
        assertTrue(entry.isTimedSpan)
        assertFalse(entry.isActive)
    }

    @Test
    fun `timed event keeps planned span through final Today assembly`() {
        val event = Event(
                id = 7,
                title = "Timed event",
                dateEpochDay = date.toEpochDay(),
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 16 * 60,
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            )

        val entry = assemble(events = listOf(event), nowMillis = at(12))
            .single { it.entryType == TimelineEntryType.EVENT_BLOCK }

        assertEquals(at(14), entry.startAtMillis)
        assertEquals(at(16), entry.endAtMillis)
        assertEquals(TimelineState.PLANNED, entry.state)
        assertTrue(entry.isTimedSpan)
    }

    @Test
    fun `point record stays on actual side`() {
        val point = Session(id = 9, title = "取快递",
            temporalType = SessionTemporalType.POINT, startAtMillis = at(10),
            endAtMillis = null, createdAtMillis = dayStart,
            updatedAtMillis = at(10))

        val entry = assemble(
            sessions = listOf(point),
            nowMillis = at(12),
        ).single { it.entryType == TimelineEntryType.SESSION_POINT }

        assertEquals(TimelineState.ACTUAL, entry.state)
        assertFalse(entry.isPlan)
        assertFalse(entry.isActive)
        assertNull(entry.durationLabel)
        assertNull(entry.endAtMillis)
    }

    @Test
    fun `session before default awake time survives final Today assembly without real boundaries`() {
        val early = Session(
                id = 10,
                title = "Early session",
                startAtMillis = at(3),
                endAtMillis = at(4),
                createdAtMillis = dayStart,
                updatedAtMillis = at(4),
            )

        val entry = assemble(sessions = listOf(early), nowMillis = at(12))
            .single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(3), entry.startAtMillis)
        assertEquals(at(4), entry.endAtMillis)
    }

    @Test
    fun `same-time semantic order survives metadata enrichment`() {
        val session = Session(
                id = 11,
                title = "Session",
                startAtMillis = at(10),
                endAtMillis = at(11),
                createdAtMillis = dayStart,
                updatedAtMillis = at(11),
            )
        val entries = assemble(
            sessions = listOf(session, Session(id = 10, title = "Point",
                temporalType = SessionTemporalType.POINT, startAtMillis = at(10),
                endAtMillis = null, createdAtMillis = dayStart, updatedAtMillis = dayStart)),
            nowMillis = at(12),
        ).filter { it.startAtMillis == at(10) }

        assertEquals(
            listOf(
                TimelineEntryType.SESSION_BLOCK,
                TimelineEntryType.SESSION_POINT,
            ),
            entries.map { it.entryType },
        )
    }

    private fun assemble(
        sessions: List<Session> = emptyList(),
        events: List<Event> = emptyList(),
        activeSession: Session? = null,
        classificationLabels: ClassificationLabels = labels,
        nowMillis: Long,
    ): List<TimelineEntryUiModel> {
        val projected = TimelineProjector.project(
            awakeDay = awake,
            sessions = sessions,
            events = events,
            asOfMillis = nowMillis,
            zoneId = zone,
        )
        assertTrue(projected is DomainResult.Success)

        return TodayUiMapper.map(
            date = date,
            awakeDay = awake,
            entries = (projected as DomainResult.Success<List<TimelineEntry>>).value,
            activeSession = activeSession,
            labels = classificationLabels,
            planItems = emptyList(),
            displayMode = TimelineDisplayMode.OVERVIEW,
            nowMillis = nowMillis,
            zoneId = zone,
            locale = Locale.ENGLISH,
            metadataSources = TimelineMetadataProjector.project(
                sessions = sessions,
                events = events.map { it },
            ),
        ).timelineEntries
    }

    private fun at(hour: Int, minute: Int = 0): Long =
        dayStart + hour * 3_600_000L + minute * 60_000L
}
