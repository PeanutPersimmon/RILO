package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.usecase.CreateSleepSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.FinishSleepCheckInUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.NewSessionInput
import io.github.peanutpersimmon.rilo.domain.voice.ExecuteVoiceActionUseCase
import io.github.peanutpersimmon.rilo.domain.voice.VoiceAction
import io.github.peanutpersimmon.rilo.domain.voice.VoiceActionDraft
import io.github.peanutpersimmon.rilo.domain.voice.VoiceExecutionResult
import io.github.peanutpersimmon.rilo.domain.voice.VoiceFieldPatch
import io.github.peanutpersimmon.rilo.domain.voice.VoiceFields
import io.github.peanutpersimmon.rilo.domain.voice.VoiceSemanticIntent
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepDurationBoundaryIntegrationTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val start = Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()

    @Test fun manualVoiceAndWidgetShareTheTwentyFourHourBoundary() = runBlocking {
        listOf(
            23 * 60L + 59L to true,
            24 * 60L to true,
            24 * 60L + 1L to false,
        ).forEach { (minutes, accepted) ->
            assertEquals("manual ${minutes}m", accepted, manualAccepts(minutes))
            assertEquals("voice ${minutes}m", accepted, voiceAccepts(minutes))
            assertEquals("widget ${minutes}m", accepted, widgetAccepts(minutes))
        }
    }

    private suspend fun manualAccepts(minutes: Long): Boolean = withRepository { repository ->
        CreateSleepSessionUseCase(repository).create(NewSessionInput(
            title = "夜间睡眠",
            kind = SessionKind.NIGHT_SLEEP,
            startAtMillis = start,
            endAtMillis = start + minutes * 60_000L,
        )) is DomainResult.Success
    }

    private suspend fun voiceAccepts(minutes: Long): Boolean = withRepository { repository ->
        val end = start + minutes * 60_000L
        val draft = VoiceActionDraft(
            action = VoiceAction.CreateSleepRecord,
            targetText = "夜间睡眠",
            fields = VoiceFields(
                title = VoiceFieldPatch.Set("夜间睡眠"),
                startAt = start,
                endAt = end,
                sleepKind = SessionKind.NIGHT_SLEEP,
            ),
            originalText = "补录睡眠",
            nowMillis = end,
            semanticIntent = VoiceSemanticIntent.SleepRecord,
        )
        ExecuteVoiceActionUseCase(repository, zone) { end }(draft) is VoiceExecutionResult.Executed
    }

    private suspend fun widgetAccepts(minutes: Long): Boolean = withRepository { repository ->
        val end = start + minutes * 60_000L
        val active = repository.startSessionIfIdle(Session(
            title = "夜间睡眠",
            kind = SessionKind.NIGHT_SLEEP,
            startAtMillis = start,
            createdAtMillis = start,
            updatedAtMillis = start,
        ))
        if (active !is DomainResult.Success) return@withRepository false
        FinishSleepCheckInUseCase(repository, { end }, { zone })() is DomainResult.Success
    }

    private suspend fun <T> withRepository(block: suspend (RoomRidoRepository) -> T): T {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        return try { block(RoomRidoRepository(database)) } finally { database.close() }
    }
}
