package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/** Written for later device execution; no schema or migration is introduced by voice input. */
@RunWith(AndroidJUnit4::class)
class VoiceSessionSafetyTest {
    private suspend fun withRepository(test: suspend (RoomRidoRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RidoDatabase::class.java).build()
        try { test(RoomRidoRepository(db)) } finally { db.close() }
    }
    private fun session(title: String, start: Long) = Session(
        title = title, startAtMillis = start, createdAtMillis = start, updatedAtMillis = start)
    private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success<T>).value

    @Test fun voiceStartCannotImplicitlyStopAnExistingRecord() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis().toMinutePrecision()
            val id = repo.startSession(session("原记录", now - 60_000)).value()
            assertTrue(repo.startSessionIfIdle(session("写论文", now)) is DomainResult.Failure)
            assertNull(repo.getSession(id)!!.endAtMillis)
            assertEquals(id, repo.getActiveSession()!!.id)
        }
    }
    @Test fun qwenMappedStartCannotCreateASecondActiveRecord() = runBlocking {
        withRepository { repo ->
            val now = Instant.ofEpochMilli(System.currentTimeMillis().toMinutePrecision())
            val first = repo.startSession(session("原记录", now.minusSeconds(60).toEpochMilli())).value()
            val draft = QwenToolCallMapper().map("start_record", """{"title":"写论文"}""",
                "开始写论文", VoiceAgentContext(now, ZoneId.systemDefault()))
            val result = ExecuteVoiceActionUseCase(repo)(draft)
            assertTrue(result is VoiceExecutionResult.Fallback)
            assertNull(repo.getSession(first)!!.endAtMillis)
            assertEquals(first, repo.getActiveSession()!!.id)
        }
    }
    @Test fun confirmationCannotStopAReplacedActiveRecord() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis().toMinutePrecision()
            val old = repo.startSession(session("写论文", now - 180_000)).value()
            repo.stopActiveSession(now - 120_000).value()
            val current = repo.startSession(session("跑步", now - 60_000)).value()
            assertTrue(repo.stopConfirmedSession(old, now) is DomainResult.Failure)
            assertNull(repo.getSession(current)!!.endAtMillis)
        }
    }
    @Test fun futureRecordsRemainRejected() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis().toMinutePrecision()
            assertTrue(repo.createPastSession(session("未来记录", now + 60_000).copy(endAtMillis = now + 120_000)) is DomainResult.Failure)
            assertTrue(repo.getSessions(now, now + 200_000).isEmpty())
        }
    }
}
