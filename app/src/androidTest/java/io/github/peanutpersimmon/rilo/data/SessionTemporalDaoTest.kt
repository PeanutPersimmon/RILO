package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionTemporalDaoTest {
    @Test fun activityPurposeQueriesUseHalfOpenRangesAndExcludeSleepAndActive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = db.sessionDao()
            val completed = dao.insert(entity("completed", 120, 180, SessionTemporalType.INTERVAL))
            val crossing = dao.insert(entity("crossing", 90, 110, SessionTemporalType.INTERVAL))
            val endingAtStart = dao.insert(entity("ending", 60, 100, SessionTemporalType.INTERVAL))
            val startingAtEnd = dao.insert(entity("starting", 200, 230, SessionTemporalType.INTERVAL))
            val point = dao.insert(entity("point", 199, null, SessionTemporalType.POINT))
            dao.insert(entity("next point", 200, null, SessionTemporalType.POINT))
            dao.insert(entity("active", 150, null, SessionTemporalType.INTERVAL))
            dao.insert(entity("night", 130, 170, SessionTemporalType.INTERVAL, SessionKind.NIGHT_SLEEP))
            dao.insert(entity("nap", 130, 170, SessionTemporalType.INTERVAL, SessionKind.NAP))
            assertEquals(setOf(completed, crossing),
                dao.getCompletedActivityEntitiesInRange(100, 200).map { it.id }.toSet())
            assertEquals(setOf(completed, crossing, point),
                dao.getActivityFactEntitiesInRange(100, 200).map { it.id }.toSet())
            assertFalse(dao.getCompletedActivityEntitiesInRange(100, 200).any {
                it.id == endingAtStart || it.id == startingAtEnd
            })
            val repository = RoomRidoRepository(db)
            assertEquals(setOf(completed, crossing), repository.getCompletedActivityIntervalsInRange(100, 200)
                .map { it.id }.toSet())
        } finally { db.close() }
    }

    @Test fun monthPointEndBoundaryAndSleepWriteInvariant() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = db.sessionDao()
            val lastPoint = dao.insert(entity("last", 999, null, SessionTemporalType.POINT))
            val nextPoint = dao.insert(entity("next", 1000, null, SessionTemporalType.POINT))
            assertEquals(listOf(lastPoint), dao.getActivityFactEntitiesInRange(0, 1000).map { it.id })
            assertEquals(listOf(nextPoint), dao.getActivityFactEntitiesInRange(1000, 2000).map { it.id })

            val repository = RoomRidoRepository(db)
            for ((index, kind) in listOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP).withIndex()) {
                val start = 120_000L + index * 240_000L
                val base = Session(title = "sleep", kind = kind, temporalType = SessionTemporalType.INTERVAL,
                    startAtMillis = start, endAtMillis = start + 60_000L,
                    createdAtMillis = 1, updatedAtMillis = 1)
                assertEquals(DomainError.InvalidTime, (repository.createPastSession(
                    base.copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)
                ) as DomainResult.Failure).error)
                val invalid = listOf(
                    base.copy(categoryId = 1), base.copy(sourceEventId = 1)
                )
                invalid.forEach { session ->
                    assertEquals(DomainError.InvalidTime, (repository.createPastSession(session)
                        as DomainResult.Failure).error)
                }
                val savedId = (repository.createPastSession(base) as DomainResult.Success).value
                assertTrue(repository.updateSession(base.copy(id = savedId, title = "edited sleep"))
                    is DomainResult.Success)
                assertEquals("edited sleep", repository.getSession(savedId)?.title)
            }
            assertEquals(1, repository.getNightSleeps().size)
            assertEquals(1, repository.getNapsInRange(0, 600_000).size)
        } finally { db.close() }
    }

    @Test fun pointRangeActiveOverlapAndStopSemantics() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = db.sessionDao()
            val interval = dao.insert(entity("interval", minute(100), minute(300), SessionTemporalType.INTERVAL))
            val point = dao.insert(entity("point", minute(200), null, SessionTemporalType.POINT))
            val farPoint = dao.insert(entity("far point", minute(1000), null, SessionTemporalType.POINT))
            val active = dao.insert(entity("active", minute(500), null, SessionTemporalType.INTERVAL))
            assertEquals(setOf(interval, point), dao.getIntersecting(minute(150), minute(250)).map { it.id }.toSet())
            assertEquals(setOf(active, farPoint), dao.getIntersecting(minute(900), minute(1100)).map { it.id }.toSet())
            assertFalse(dao.getIntersecting(minute(2000), minute(2100)).any { it.id == point || it.id == farPoint })
            assertEquals(listOf(active), dao.getActiveSessions().map { it.id })
            assertEquals(1, dao.countActiveSessions())
            assertEquals(1, dao.countOverlaps(minute(200), minute(250)))
            assertEquals(0, dao.countOverlaps(minute(300), minute(400)))
            val repository = RoomRidoRepository(db)
            val savedPoint = repository.createPastSession(Session(title = "inside", temporalType = SessionTemporalType.POINT,
                startAtMillis = minute(220), createdAtMillis = 1, updatedAtMillis = 1))
            assertTrue(savedPoint is DomainResult.Success)
            val overlap = repository.createPastSession(Session(title = "overlap", startAtMillis = minute(220),
                endAtMillis = minute(320), createdAtMillis = 1, updatedAtMillis = 1))
            assertEquals(DomainError.SessionOverlap, (overlap as DomainResult.Failure).error)
            val stopped = repository.stopActiveSession(minute(600))
            assertEquals(active, (stopped as DomainResult.Success).value.id)
            assertEquals(SessionTemporalType.POINT, dao.getById(point)!!.temporalType)
            assertNull(dao.getById(point)!!.endAtMillis)
            assertTrue(dao.getActiveSessions().isEmpty())
            assertTrue(repository.stopActiveSession(minute(700)) is DomainResult.Failure)
        } finally {
            db.close()
        }
    }

    private fun entity(title: String, start: Long, end: Long?, type: SessionTemporalType,
        kind: SessionKind = SessionKind.ACTIVITY) =
        SessionEntity(title = title, kind = kind, temporalType = type,
            startAtMillis = start, endAtMillis = end, createdAtMillis = 1, updatedAtMillis = 1)

    private fun minute(value: Long): Long = value * 60_000L
}

