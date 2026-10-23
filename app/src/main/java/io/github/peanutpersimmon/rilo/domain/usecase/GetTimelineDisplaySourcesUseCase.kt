package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.service.TimelineProjector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TimelineDisplaySources(val sessions: List<Session>, val events: List<Event>)

data class TimelineMetadataSources(
    val sourceEventTitlesBySessionId: Map<Long, String>,
)

object TimelineMetadataProjector {
    fun project(
        sessions: List<Session>,
        events: List<Event>,
    ): TimelineMetadataSources {
        val eventTitlesById = events.associate { it.id to it.title }
        return TimelineMetadataSources(
            sourceEventTitlesBySessionId = sessions.mapNotNull { session ->
                val title = session.sourceEventId?.let(eventTitlesById::get)
                    ?: return@mapNotNull null
                session.id to title
            }.toMap(),
        )
    }
}

data class TodayStableSnapshot(
    val calendarDate: java.time.LocalDate,
    val nightSleeps: List<Session>,
    val displaySources: TimelineDisplaySources,
    val metadataSources: TimelineMetadataSources,
    val labels: ClassificationLabels,
)

/** Read-only display inputs. Does not change occupancy, statistics or stored records. */
class GetTimelineDisplaySourcesUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(awake: AwakeDay, zone: ZoneId): TimelineDisplaySources {
        val range = TimelineProjector.projectionRange(awake, zone)
        val first = Instant.ofEpochMilli(range.startMillis).atZone(zone).toLocalDate().toEpochDay()
        val last = Instant.ofEpochMilli(range.endMillis - 1).atZone(zone).toLocalDate().toEpochDay()
        return TimelineDisplaySources(
            sessions = repository.getSessions(range.startMillis - 1, range.endMillis),
            events = repository.getEvents(first, last, range.startMillis, range.endMillis),
        )
    }

    suspend operator fun invoke(window: AwakeDisplayWindow, zone: ZoneId): TimelineDisplaySources {
        val range = TimelineProjector.projectionRange(window, zone)
        val first = Instant.ofEpochMilli(range.startMillis).atZone(zone).toLocalDate().toEpochDay()
        val last = Instant.ofEpochMilli(range.endMillis - 1).atZone(zone).toLocalDate().toEpochDay()
        return TimelineDisplaySources(
            sessions = repository.getSessions(range.startMillis - 1, range.endMillis),
            events = repository.getEvents(first, last, range.startMillis, range.endMillis),
        )
    }

    suspend operator fun invoke(displayDate: LocalDate, zone: ZoneId): TimelineDisplaySources {
        val rangeStart = displayDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = displayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val epochDay = displayDate.toEpochDay()
        return TimelineDisplaySources(
            sessions = repository.getSessions(rangeStart - 1, rangeEnd),
            events = repository.getEvents(epochDay, epochDay, rangeStart, rangeEnd),
        )
    }
}

/** Loads database-backed Today inputs once; clock projection remains a pure in-memory operation. */
class GetTodayStableSnapshotUseCase(private val repository: RidoCoreRepository) {
    private val getDisplaySources = GetTimelineDisplaySourcesUseCase(repository)

    suspend operator fun invoke(request: AwakeDayRequest): TodayStableSnapshot {
        val nightSleeps = repository.getNightSleeps()
        val displaySources = getDisplaySources(request.calendarDate, request.zoneId)
        val categories = repository.getCategories()
        val candidateEvents = displaySources.events
        val candidateEventIds = candidateEvents.mapTo(mutableSetOf()) { it.id }
        val missingSourceEventIds = displaySources.sessions.mapNotNullTo(mutableSetOf()) {
            it.sourceEventId
        } - candidateEventIds
        val sourceEvents = candidateEvents + if (missingSourceEventIds.isEmpty()) {
            emptyList()
        } else {
            repository.getEventsByIds(missingSourceEventIds)
        }
        return TodayStableSnapshot(
            calendarDate = request.calendarDate,
            nightSleeps = nightSleeps,
            displaySources = displaySources,
            metadataSources = TimelineMetadataProjector.project(
                sessions = displaySources.sessions,
                events = sourceEvents,
            ),
            labels = ClassificationLabelsProjector.project(categories),
        )
    }
}
