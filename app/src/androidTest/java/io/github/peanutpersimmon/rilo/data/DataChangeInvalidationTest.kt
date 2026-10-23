package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataChangeInvalidationTest {
    @Test
    fun initialSignalIsEmittedOnceWithoutBusinessSelects() = runBlocking {
        withDatabase { database, queries ->
            queries.clear()
            val probe = SignalProbe.start(this, RoomRidoRepository(database).observeDataChanges())
            try {
                probe.awaitSignal()
                probe.assertNoSignal()
                assertNoBusinessSelects(queries)
            } finally {
                probe.close()
            }
        }
    }

    @Test
    fun allTodayAndPlanTablesSignalWithoutObserverBusinessSelects() = runBlocking {
        withDatabase { database, queries ->
            val repository = RoomRidoRepository(database)
            val probe = SignalProbe.start(this, repository.observeDataChanges())
            try {
                probe.awaitSignal()
                queries.clear()

                val event = eventEntity(title = "Event")
                val eventId = database.eventDao().insert(event)
                probe.awaitSignal()
                database.eventDao().update(event.copy(id = eventId, title = "Updated event"))
                probe.awaitSignal()

                val session = sessionEntity(title = "Session", kind = SessionKind.ACTIVITY, start = 1_000L)
                val sessionId = database.sessionDao().insert(session)
                probe.awaitSignal()
                database.sessionDao().finish(sessionId, endAtMillis = 2_000L, updatedAtMillis = 2_000L)
                probe.awaitSignal()

                val sleep = sessionEntity(title = "Sleep", kind = SessionKind.NIGHT_SLEEP, start = 3_000L)
                val sleepId = database.sessionDao().insert(sleep)
                probe.awaitSignal()
                database.sessionDao().finish(sleepId, endAtMillis = 4_000L, updatedAtMillis = 4_000L)
                probe.awaitSignal()

                val category = categoryEntity(name = "Category")
                val categoryId = database.categoryDao().insert(category)
                probe.awaitSignal()
                database.categoryDao().update(category.copy(id = categoryId, name = "Updated category"))
                probe.awaitSignal()

                probe.assertNoSignal()
                assertNoBusinessSelects(queries)
            } finally {
                probe.close()
            }
        }
    }

    @Test
    fun oneTransactionAcrossMainTablesEmitsOnce() = runBlocking {
        withDatabase { database, _ ->
            val probe = SignalProbe.start(this, RoomRidoRepository(database).observeDataChanges())
            try {
                probe.awaitSignal()

                database.withTransaction {
                    database.eventDao().insert(eventEntity(title = "Event"))
                    database.sessionDao().insert(sessionEntity(title = "Record", kind = SessionKind.ACTIVITY, start = 1_000L))
                }

                probe.awaitSignal()
                probe.assertNoSignal()
            } finally {
                probe.close()
            }
        }
    }

    private suspend fun withDatabase(
        test: suspend (RidoDatabase, MutableList<String>) -> Unit,
    ) {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val directExecutor = Executor { command -> command.run() }
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RidoDatabase::class.java,
        ).setQueryCallback(
            RoomDatabase.QueryCallback { sql, _ -> queries.add(sql) },
            directExecutor,
        ).build()
        try {
            test(database, queries)
        } finally {
            database.close()
        }
    }

    private fun assertNoBusinessSelects(queries: List<String>) {
        val businessTables = listOf(
            "sessions",
            "events",
            "categories",
            "keyword_rules",
        )
        val businessSelects = synchronized(queries) {
            queries.filter { sql ->
                val normalized = sql.trim().lowercase()
                normalized.startsWith("select") && businessTables.any { table ->
                    Regex("\\b${Regex.escape(table)}\\b").containsMatchIn(normalized)
                }
            }
        }
        assertFalse("Observer executed business SELECTs: $businessSelects", businessSelects.isNotEmpty())
    }

    private fun eventEntity(title: String) = EventEntity(
        title = title,
        dateEpochDay = 1L,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun sessionEntity(title: String, kind: SessionKind, start: Long) = SessionEntity(
        title = title,
        kind = kind,
        temporalType = SessionTemporalType.INTERVAL,
        startAtMillis = start,
        createdAtMillis = start,
        updatedAtMillis = start,
    )

    private fun categoryEntity(name: String) = CategoryEntity(
        name = name,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private class SignalProbe(
        private val channel: Channel<Unit>,
        private val job: Job,
    ) {
        suspend fun awaitSignal() {
            withTimeout(5_000L) { channel.receive() }
        }

        suspend fun assertNoSignal() {
            assertNull(withTimeoutOrNull(300L) { channel.receive() })
        }

        suspend fun close() {
            job.cancelAndJoin()
            channel.close()
        }

        companion object {
            fun start(scope: CoroutineScope, flow: Flow<Unit>): SignalProbe {
                val channel = Channel<Unit>(Channel.UNLIMITED)
                val job = scope.launch { flow.collect { channel.send(Unit) } }
                return SignalProbe(channel, job)
            }
        }
    }
}
