package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.AwakeDay
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntry
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.service.AwakeDayCalculator
import io.github.peanutpersimmon.rilo.domain.service.TimelineProjector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rilo.domain.time.plannedInterval

data class AwakeDayRequest(
    val calendarDate: LocalDate,
    val defaultWakeMinute: Int,
    val defaultSleepMinute: Int,
    val zoneId: ZoneId,
    val asOfMillis: Long,
)

class GetAwakeDayUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(request: AwakeDayRequest): AwakeDay = AwakeDayCalculator.calculate(
        calendarDate = request.calendarDate,
        nightSleeps = repository.getNightSleeps(),
        defaultWakeMinute = request.defaultWakeMinute,
        defaultSleepMinute = request.defaultSleepMinute,
        zoneId = request.zoneId,
        asOfMillis = request.asOfMillis,
    )
}

class GetTimelineUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(request: AwakeDayRequest): DomainResult<List<TimelineEntry>> {
        val awakeDay = AwakeDayCalculator.calculate(
            calendarDate = request.calendarDate,
            nightSleeps = repository.getNightSleeps(),
            defaultWakeMinute = request.defaultWakeMinute,
            defaultSleepMinute = request.defaultSleepMinute,
            zoneId = request.zoneId,
            asOfMillis = request.asOfMillis,
        )
        val projectionRange = TimelineProjector.projectionRange(awakeDay, request.zoneId)
        val firstDate = Instant.ofEpochMilli(projectionRange.startMillis)
            .atZone(request.zoneId).toLocalDate()
        val lastDate = Instant.ofEpochMilli(projectionRange.endMillis - 1)
            .atZone(request.zoneId).toLocalDate()
        val sessions = repository.getSessions(
            projectionRange.startMillis - 1,
            projectionRange.endMillis,
        )
        val events = repository.getEvents(
            firstDate.toEpochDay(),
            lastDate.toEpochDay(),
            projectionRange.startMillis,
            projectionRange.endMillis,
        )
        return TimelineProjector.project(
            awakeDay,
            sessions,
            events,
            request.asOfMillis,
            request.zoneId,
        )
    }
}

class ObserveTimelineDataChangesUseCase(private val repository: RidoCoreRepository) {
    operator fun invoke(): Flow<Unit> = repository.observeDataChanges()
}

data class CategoryPresentation(
    val name: String,
    val colorKey: String,
    val isArchived: Boolean,
)

data class ClassificationLabels(
    val categoriesById: Map<Long, CategoryPresentation>,
)

class GetClassificationLabelsUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(): ClassificationLabels = ClassificationLabelsProjector.project(repository.getCategories())
}

object ClassificationLabelsProjector {
    fun project(categories: List<io.github.peanutpersimmon.rilo.domain.model.Category>): ClassificationLabels = ClassificationLabels(
        categoriesById = categories.associate {
            it.id to CategoryPresentation(it.name, it.colorKey, it.isArchived)
        },
    )
}

enum class TodayPlanSource { EVENT }

data class TodayPlanItem(
    val sourceId: Long,
    val source: TodayPlanSource,
    val title: String,
    val startAtMillis: Long?,
    val endAtMillis: Long?,
    val completed: Boolean = false,
    val detail: String? = null,
    val categoryId: Long? = null,
)

class GetTodayPlanUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId,
        asOfMillis: Long,
    ): List<TodayPlanItem> {
        val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return TodayPlanProjector.project(
            date = date,
            zoneId = zoneId,
            asOfMillis = asOfMillis,
            events = repository.getEvents(date.toEpochDay(), date.toEpochDay(), dayStart, dayEnd),
        )
    }
}

object TodayPlanProjector {
    fun project(
        date: LocalDate,
        zoneId: ZoneId,
        asOfMillis: Long,
        events: List<io.github.peanutpersimmon.rilo.domain.model.Event>,
    ): List<TodayPlanItem> {
        val epochDay = date.toEpochDay()
        val eventItems = events.filter { it.dateEpochDay == epochDay }.map { event ->
                TodayPlanItem(event.id, TodayPlanSource.EVENT, event.title,
                    event.startMinuteOfDay?.let { epochDayMinuteToMillis(epochDay, it, zoneId) },
                    event.endMinuteOfDay?.let { epochDayMinuteToMillis(event.endDateEpochDay ?: epochDay, it, zoneId) },
                    categoryId = event.categoryId)
            }
        return eventItems.sortedWith(
            compareBy<TodayPlanItem> { it.startAtMillis == null }
                .thenBy { it.startAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.source.ordinal }
                .thenBy { it.sourceId },
        )
    }
}
