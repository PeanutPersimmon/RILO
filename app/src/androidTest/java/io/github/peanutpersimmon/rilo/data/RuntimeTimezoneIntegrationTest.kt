package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import io.github.peanutpersimmon.rilo.domain.usecase.GetTodayStableSnapshotUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveActiveSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveTimelineDataChangesUseCase
import io.github.peanutpersimmon.rilo.presentation.today.TodayUiState
import io.github.peanutpersimmon.rilo.presentation.today.TodayViewModel
import io.github.peanutpersimmon.rilo.domain.usecase.GetStatisticsUseCase
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsUiState
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsViewModel
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeTimezoneIntegrationTest {
    @Test fun statisticsRevalidatesTodayWhenReturningAfterMidnight() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RidoDatabase::class.java,
        ).build()
        try {
            val repository = RoomRidoRepository(database)
            val zone = ZoneId.of("UTC")
            var now = Instant.parse("2026-10-17T23:59:00Z").toEpochMilli()
            withContext(Dispatchers.Main) {
                val model = StatisticsViewModel(
                    ObserveTimelineDataChangesUseCase(repository),
                    GetStatisticsUseCase(repository),
                    zone = zone,
                    timeProvider = CurrentTimeProvider { now },
                )
                model.setPageVisible(true)
                val before = withTimeout(5_000) {
                    model.uiState.first { it is StatisticsUiState.Ready }
                } as StatisticsUiState.Ready
                assertEquals(instantDate(now, zone), before.selection.period.anchor)

                model.setPageVisible(false)
                now = Instant.parse("2026-10-18T00:01:00Z").toEpochMilli()
                model.setPageVisible(true)
                val after = withTimeout(5_000) {
                    model.uiState.first {
                        it is StatisticsUiState.Ready &&
                            it.selection.period.anchor == instantDate(now, zone)
                    }
                } as StatisticsUiState.Ready
                assertEquals(instantDate(now, zone), after.selection.period.anchor)
            }
        } finally {
            database.close()
        }
    }

    @Test fun todayQueryAndProjectionMoveTogetherWhenRuntimeZoneChanges() = runBlocking {
        val original = RuntimeZoneId.current
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RidoDatabase::class.java,
        ).build()
        try {
            val repository = RoomRidoRepository(database)
            val instant = Instant.now()
            val east = ZoneId.of("Pacific/Kiritimati")
            val west = ZoneId.of("Pacific/Honolulu")
            val eastDate = instant.atZone(east).toLocalDate()
            val westDate = instant.atZone(west).toLocalDate()
            assertTrue(eastDate != westDate)
            listOf(eastDate to "East event", westDate to "West event").forEach { (date, title) ->
                assertTrue(repository.upsertEvent(Event(
                    title = title,
                    dateEpochDay = date.toEpochDay(),
                    startMinuteOfDay = 12 * 60,
                    createdAtMillis = instant.toEpochMilli(),
                    updatedAtMillis = instant.toEpochMilli(),
                )) is DomainResult.Success)
            }

            RuntimeZoneId.refresh(east)
            withContext(Dispatchers.Main) {
                val model = TodayViewModel(
                    ObserveTimelineDataChangesUseCase(repository),
                    ObserveActiveSessionUseCase(repository),
                    GetTodayStableSnapshotUseCase(repository),
                )
                val eastState = withTimeout(5_000) {
                    model.uiState.first { it is TodayUiState.Content && it.zoneId == east }
                } as TodayUiState.Content
                assertEquals(listOf("East event"), eastState.timelineEntries.map { it.title })

                RuntimeZoneId.refresh(west)
                val westState = withTimeout(5_000) {
                    model.uiState.first { it is TodayUiState.Content && it.zoneId == west }
                } as TodayUiState.Content
                assertEquals(listOf("West event"), westState.timelineEntries.map { it.title })
            }
        } finally {
            RuntimeZoneId.refresh(original)
            database.close()
        }
    }

    private fun instantDate(millis: Long, zone: ZoneId) =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
