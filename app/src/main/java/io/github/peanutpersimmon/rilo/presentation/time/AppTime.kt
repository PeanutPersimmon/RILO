package io.github.peanutpersimmon.rilo.presentation.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive

fun interface CurrentTimeProvider {
    fun nowMillis(): Long
    val changes: Flow<Unit> get() = emptyFlow()
}

object SystemCurrentTimeProvider : CurrentTimeProvider {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = refreshes
    override fun nowMillis(): Long = System.currentTimeMillis()
    fun refresh() { refreshes.tryEmit(Unit) }
}

/** Re-reads wall time after every aligned delay; elapsed time never depends on tick count. */
@OptIn(ExperimentalCoroutinesApi::class)
fun alignedTicks(
    timeProvider: CurrentTimeProvider,
    periodMillis: Long,
): Flow<Long> = timeProvider.changes.onStart { emit(Unit) }.flatMapLatest { flow {
    require(periodMillis > 0)
    while (currentCoroutineContext().isActive) {
        val now = timeProvider.nowMillis()
        emit(now)
        val remainder = Math.floorMod(now, periodMillis)
        delay(if (remainder == 0L) periodMillis else periodMillis - remainder)
    }
} }

fun alignedSecondTicks(timeProvider: CurrentTimeProvider): Flow<Long> =
    alignedTicks(timeProvider, 1_000L)

data class ZonedLocalDay(val date: LocalDate, val zoneId: ZoneId)

/** Emits immediately when visible, at each local midnight, and whenever the runtime zone changes. */
@OptIn(ExperimentalCoroutinesApi::class)
fun localDayRefreshes(
    pageVisible: Flow<Boolean>,
    zoneIds: Flow<ZoneId>,
    timeProvider: CurrentTimeProvider,
    delayMillis: suspend (Long) -> Unit = { delay(it) },
): Flow<ZonedLocalDay> = combine(pageVisible, zoneIds, timeProvider.changes.onStart { emit(Unit) }) {
        visible, zone, _ -> visible to zone
    }
    .flatMapLatest { (visible, zone) ->
        if (!visible) emptyFlow() else flow {
            while (currentCoroutineContext().isActive) {
                val now = timeProvider.nowMillis()
                val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                emit(ZonedLocalDay(date, zone))
                val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                delayMillis((nextMidnight - now).coerceAtLeast(1L))
            }
        }
    }.distinctUntilChanged()
