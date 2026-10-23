package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.model.TimelineState
import io.github.peanutpersimmon.rilo.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rilo.domain.usecase.TimelineMetadataSources
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMetadataMapperContractTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.ofEpochDay(0)
    private val labels = ClassificationLabels(emptyMap())

    @Test fun metadataEnrichmentPreservesCountOrderAndEveryCanonicalField() {
        val entries = listOf(
            ui(1, TimelineEntryType.SESSION_BLOCK, TimelineState.ACTUAL, 100, 900),
            ui(2, TimelineEntryType.SESSION_POINT, TimelineState.ACTUAL, 100, null),
            ui(4, TimelineEntryType.EVENT_BLOCK, TimelineState.PLANNED, 100, null),
            ui(5, TimelineEntryType.EVENT_BLOCK, TimelineState.PLANNED, 100, 400),
        )
        val sources = TimelineMetadataSources(
            sourceEventTitlesBySessionId = mapOf(1L to "Source event", 2L to "Point source"),
        )

        val result = map(entries, sources)

        assertEquals(entries.size, result.size)
        assertEquals(entries.map(::core), result.map(::core))
        assertEquals(entries.map { it.sourceId }, result.map { it.sourceId })
        assertEquals(entries.map { it.title }, result.map { it.title })
    }

    @Test fun sessionSourceAddsEventTitleWithoutRebuildingTheEntry() {
        val event = Event(
            id = 20,
            title = "Source event",
            dateEpochDay = 0,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        val session = Session(
            id = 1,
            title = "Conflicting raw title",
            startAtMillis = 500,
            endAtMillis = null,
            categoryId = 88,
            sourceEventId = event.id,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        val entry = ui(1, TimelineEntryType.SESSION_BLOCK, TimelineState.ACTUAL, 100, 900)
            .copy(title = "Canonical title", categoryLabel = "Canonical category")

        val result = map(
            listOf(entry),
            TimelineMetadataSources(mapOf(session.id to event.title)),
        ).single()

        assertEquals(entry, result.copy(sourceEventTitle = null))
        assertEquals("Source event", result.sourceEventTitle)
    }

    @Test fun pointRecordKeepsActualState() {
        val entry = ui(7, TimelineEntryType.SESSION_POINT, TimelineState.ACTUAL, 700, null)
            .copy(
                completed = false,
            )
        val result = map(
            listOf(entry),
            TimelineMetadataSources(mapOf(7L to "Source event")),
        ).single()

        assertEquals(core(entry), core(result))
        assertEquals(TimelineState.ACTUAL, result.state)
        assertEquals("Source event", result.sourceEventTitle)
        assertTrue(!result.completed)
        assertNull(result.endAtMillis)
    }

    private fun map(
        entries: List<TimelineEntryUiModel>,
        sources: TimelineMetadataSources,
    ): List<TimelineEntryUiModel> = TimelineEndpointMapper.map(
        entries = entries,
        sources = sources,
        labels = labels,
        zone = zone,
        date = date,
        nowMillis = 3_600_000L,
    )

    private fun ui(
        id: Long,
        type: TimelineEntryType,
        state: TimelineState,
        start: Long,
        end: Long?,
    ) = TimelineEntryUiModel(
        sourceId = id,
        startAtMillis = start,
        endAtMillis = end,
        title = "Canonical $id",
        timeLabel = "canonical-time-$id",
        durationLabel = end?.let { "canonical-duration-$id" },
        entryType = type,
        state = state,
        categoryLabel = "category-$id",
        location = "location-$id",
        note = "note-$id",
        isActive = false,
        axisTimes = listOfNotNull(start, end),
    )

    private fun core(entry: TimelineEntryUiModel) = CanonicalCore(
        sourceId = entry.sourceId,
        startAtMillis = entry.startAtMillis,
        endAtMillis = entry.endAtMillis,
        entryType = entry.entryType,
        state = entry.state,
        axisTimes = entry.axisTimes,
    )

    private data class CanonicalCore(
        val sourceId: Long?,
        val startAtMillis: Long,
        val endAtMillis: Long?,
        val entryType: TimelineEntryType,
        val state: TimelineState,
        val axisTimes: List<Long>,
    )
}
