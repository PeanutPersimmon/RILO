package io.github.peanutpersimmon.rilo.presentation.today

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.usecase.*
import java.time.LocalDate
import java.time.ZoneId

/** Adds display-only metadata without changing canonical projection semantics or ordering. */
internal object TimelineEndpointMapper {
    fun map(entries: List<TimelineEntryUiModel>, sources: TimelineMetadataSources,
        labels: ClassificationLabels, zone: ZoneId, date: LocalDate = LocalDate.now(zone),
        nowMillis: Long = System.currentTimeMillis()): List<TimelineEntryUiModel> {
        return entries.map { entry ->
            when (entry.entryType) {
                TimelineEntryType.SESSION_BLOCK, TimelineEntryType.SESSION_POINT -> entry.copy(
                    sourceEventTitle = entry.sourceEventTitle
                        ?: entry.sourceId?.let(sources.sourceEventTitlesBySessionId::get),
                )
                else -> entry
            }
        }
    }
}
