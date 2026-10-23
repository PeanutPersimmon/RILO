package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.usecase.FinishSleepCheckInUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StartSleepCheckInUseCase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepWidgetDataChainTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun repeatedSleepCheckInCreatesOnlyOneActiveNightSleep() = runBlocking {
        withRepository { repository ->
            val now = System.currentTimeMillis()
            val useCase = StartSleepCheckInUseCase(repository) { now }

            assertTrue(useCase() is DomainResult.Success)
            assertTrue(useCase() is DomainResult.Failure)

            val active = repository.getActiveSession()!!
            assertEquals(SessionKind.NIGHT_SLEEP, active.kind)
            assertEquals("夜间睡眠", active.title)
            assertNull(active.endAtMillis)
            assertEquals(1, repository.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).size)
        }
    }

    @Test
    fun activityPreventsSleepStartAndWakeDoesNotStopIt() = runBlocking {
        withRepository { repository ->
            val now = System.currentTimeMillis()
            val activityId = successValue(
                repository.startSession(
                    activeSession("写论文", SessionKind.ACTIVITY, now - 60_000L),
                ),
            )

            assertTrue(StartSleepCheckInUseCase(repository) { now }() is DomainResult.Failure)
            assertTrue(
                FinishSleepCheckInUseCase(repository, { now }, { zone })() is DomainResult.Failure,
            )

            val activity = repository.getSession(activityId)!!
            assertEquals(SessionKind.ACTIVITY, activity.kind)
            assertNull(activity.endAtMillis)
            assertEquals(activityId, repository.getActiveSession()!!.id)
        }
    }

    @Test
    fun wakeWithoutActiveSleepDoesNotWrite() = runBlocking {
        withRepository { repository ->
            val result = FinishSleepCheckInUseCase(
                repository = repository,
                nowMillis = System::currentTimeMillis,
                zoneId = { zone },
            )()

            assertTrue(result is DomainResult.Failure)
            assertTrue(repository.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
        }
    }

    @Test
    fun finishingDaytimeSleepAtomicallyStoresNapAndTitle() = runBlocking {
        withRepository { repository ->
            val day = LocalDate.now(zone).minusDays(1)
            val start = day.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
            val end = day.atTime(13, 30).atZone(zone).toInstant().toEpochMilli()
            val id = successValue(
                repository.startSession(
                    activeSession("夜间睡眠", SessionKind.NIGHT_SLEEP, start),
                ),
            )

            val result = FinishSleepCheckInUseCase(repository, { end }, { zone })()

            assertTrue(result is DomainResult.Success)
            val saved = repository.getSession(id)!!
            assertEquals(SessionKind.NAP, saved.kind)
            assertEquals("小憩", saved.title)
            assertEquals(end, saved.endAtMillis)
            assertNull(repository.getActiveSession())
        }
    }

    private suspend fun withRepository(test: suspend (RoomRidoRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            test(RoomRidoRepository(database))
        } finally {
            database.close()
        }
    }

    private fun activeSession(
        title: String,
        kind: SessionKind,
        startAtMillis: Long,
    ) = Session(
        title = title,
        kind = kind,
        startAtMillis = startAtMillis,
        endAtMillis = null,
        createdAtMillis = startAtMillis,
        updatedAtMillis = startAtMillis,
    )

    private fun <T> successValue(result: DomainResult<T>): T =
        (result as DomainResult.Success<T>).value
}
