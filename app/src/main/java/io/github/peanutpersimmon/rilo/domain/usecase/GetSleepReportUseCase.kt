package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.service.SleepAnnualReport
import io.github.peanutpersimmon.rilo.domain.service.SleepMonthlyReport
import io.github.peanutpersimmon.rilo.domain.service.SleepReportCalculator
import io.github.peanutpersimmon.rilo.domain.service.SleepDayResolver
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Reads the existing RILO Sessions; period membership is decided after sleep-day projection. */
class GetSleepReportUseCase(private val repository: RidoCoreRepository) {
    suspend fun earliestSleepYear(zone: ZoneId): Int? =
        (repository.getNightSleeps() + repository.getNapsInRange(Long.MIN_VALUE, Long.MAX_VALUE))
            .minOfOrNull { SleepDayResolver.resolve(it, zone).year }

    suspend fun monthly(month: YearMonth, zone: ZoneId, asOfMillis: Long): SleepMonthlyReport {
        val today = Instant.ofEpochMilli(asOfMillis).atZone(zone).toLocalDate()
        return SleepReportCalculator.monthly(repository.getNightSleeps(),
            repository.getNapsInRange(Long.MIN_VALUE, Long.MAX_VALUE), month, zone, today, asOfMillis)
    }

    suspend fun annual(year: Int, zone: ZoneId, asOfMillis: Long): SleepAnnualReport {
        val today = Instant.ofEpochMilli(asOfMillis).atZone(zone).toLocalDate()
        return SleepReportCalculator.annual(repository.getNightSleeps(),
            repository.getNapsInRange(Long.MIN_VALUE, Long.MAX_VALUE), year, zone, today, asOfMillis)
    }
}
