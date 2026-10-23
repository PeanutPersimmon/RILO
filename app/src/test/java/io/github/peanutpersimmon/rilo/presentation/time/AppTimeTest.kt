package io.github.peanutpersimmon.rilo.presentation.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppTimeTest {
    @Test fun visibleStatisticsRefreshesExactlyAtLocalMidnight() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        var now = Instant.parse("2026-10-17T15:59:00Z").toEpochMilli()
        val waited = mutableListOf<Long>()

        val days = localDayRefreshes(
            pageVisible = MutableStateFlow(true),
            zoneIds = MutableStateFlow(zone),
            timeProvider = CurrentTimeProvider { now },
            delayMillis = { duration ->
                waited += duration
                now += duration
                yield()
            },
        ).take(2).toList()

        assertEquals(60_000L, waited.first())
        assertEquals(listOf(86_400_000L), waited.drop(1))
        assertEquals(
            listOf(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18)),
            days.map { it.date },
        )
        assertEquals(listOf(zone, zone), days.map { it.zoneId })
    }

    @Test fun runtimeZoneChangeRecomputesLocalDayWithoutProcessRestart() = runTest {
        val zones = MutableStateFlow(ZoneId.of("UTC"))
        val now = Instant.parse("2026-01-01T00:30:00Z").toEpochMilli()
        val result = async {
            localDayRefreshes(
                pageVisible = MutableStateFlow(true),
                zoneIds = zones,
                timeProvider = CurrentTimeProvider { now },
                delayMillis = { awaitCancellation() },
            ).take(2).toList()
        }

        runCurrent()
        zones.value = ZoneId.of("America/Los_Angeles")
        runCurrent()

        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)),
            result.await().map { it.date },
        )
    }
}
