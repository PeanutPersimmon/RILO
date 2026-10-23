package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.service.ActualStatistics
import io.github.peanutpersimmon.rilo.domain.service.ActualStatisticsCalculator
import io.github.peanutpersimmon.rilo.domain.service.StatisticsPeriod
import java.time.ZoneId
import java.time.YearMonth

data class StatisticsSources(val actual: ActualStatistics, val nightSleeps: List<Session>, val naps: List<Session>, val labels: ClassificationLabels)

class GetStatisticsUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(period: StatisticsPeriod, sleepMonth: YearMonth, zone: ZoneId, now: Long): DomainResult<StatisticsSources> {
        val interval = period.interval(zone)
        val sessions = repository.getCompletedActivityIntervalsInRange(interval.startMillis, interval.endMillis)
        val sleepStart = sleepMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sleepEnd = sleepMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val naps = repository.getNapsInRange(sleepStart, sleepEnd)
        val actual = ActualStatisticsCalculator.calculate(sessions, period, zone, now)
        return when (actual) {
            is DomainResult.Failure -> actual
            is DomainResult.Success -> DomainResult.Success(StatisticsSources(actual.value, repository.getNightSleeps(),
                naps,
                GetClassificationLabelsUseCase(repository)()))
        }
    }
}
