package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentEngine
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentResult
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentUnavailableReason
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenAudioTurnController
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceViewModel
import io.github.peanutpersimmon.rilo.domain.usecase.CreatePastSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.NewSessionInput
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceQuickExecutionTest {
    private suspend fun withRepository(block: suspend (RoomRidoRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RidoDatabase::class.java).build()
        try { block(RoomRidoRepository(db)) } finally { db.close() }
    }

    @Test fun voiceSecondsArePersistedAsMinutesAndDoNotOverlapAdjacentManualRecord() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:37.456Z")
            val draft = QwenToolCallMapper().map("create_record", """{
                "title":"语音记录","temporal_type":"INTERVAL",
                "start_at":"2026-09-26T13:00:45.123456789+08:00",
                "end_at":"2026-09-26T13:30:59.999999999+08:00"
            }""", "刚才完成了一段记录", VoiceAgentContext(now, zone))

            val voiceResult = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(draft)
                as VoiceExecutionResult.Executed
            val voiceRecord = repo.getSession(voiceResult.receipt.undo.after.records.single().id)!!
            assertEquals(0L, Math.floorMod(voiceRecord.startAtMillis, 60_000L))
            assertEquals(0L, Math.floorMod(requireNotNull(voiceRecord.endAtMillis), 60_000L))

            val adjacent = CreatePastSessionUseCase(repo)(NewSessionInput(
                title = "手动紧邻记录",
                startAtMillis = requireNotNull(voiceRecord.endAtMillis),
                endAtMillis = requireNotNull(voiceRecord.endAtMillis) + 30 * 60_000L,
            ))
            assertTrue(adjacent is DomainResult.Success)
            assertEquals(2, repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).size)
        }
    }

    @Test fun historicalFactCreatesPointRecordAndUndoDeletesIt() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val completedAt = now.atZone(zone).toLocalDate().minusDays(1)
                .atTime(20, 0).atZone(zone).toOffsetDateTime()
            val draft = QwenToolCallMapper().map("create_record",
                """{"title":"拿快递","temporal_type":"POINT","occurred_at":"$completedAt"}""",
                "昨天晚上八点拿到快递了", VoiceAgentContext(now, zone))
            val result = ExecuteVoiceActionUseCase(repo, zone)(draft) as VoiceExecutionResult.Executed
            val record = result.receipt.undo.after.records.single()
            assertEquals(SessionTemporalType.POINT, record.temporalType)
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertNull(repo.getSession(record.id))
        }
    }

    @Test fun rawCoreSentenceSavesToRoomAndUndoRemovesOnlyCreatedEvent() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val day = now.atZone(zone).toLocalDate()
            val start = day.atTime(19, 0).atZone(zone).toOffsetDateTime()
            val end = day.atTime(20, 0).atZone(zone).toOffsetDateTime()
            val draft = QwenToolCallMapper().map("create_event",
                """{"title":"选修课","temporal_type":"INTERVAL","start_at":"$start","end_at":"$end","location":"父亲楼"}""",
                "今晚19-20在父亲楼有一节选修课", VoiceAgentContext(now, zone))
            val result = ExecuteVoiceActionUseCase(repo, zone)(draft) as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.events.single()
            assertEquals("选修课", saved.title)
            assertEquals("父亲楼", saved.location)
            assertEquals(now.atZone(zone).toLocalDate().toEpochDay(), saved.dateEpochDay)
            assertEquals(1140, saved.startMinuteOfDay)
            assertEquals(1200, saved.endMinuteOfDay)
            assertEquals(saved, repo.getEvent(saved.id))
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertNull(repo.getEvent(saved.id))
        }
    }

    @Test fun unavailableAiKeepsTextAndDoesNotWrite() = runBlocking {
        withRepository { repo ->
            withContext(Dispatchers.Main) {
                val unavailable = object : VoiceAgentEngine {
                    override suspend fun executeText(text: String, context: VoiceAgentContext) =
                        VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NetworkOrService, "offline")
                    override suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext) =
                        VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NetworkOrService, "offline")
                }
                val model = VoiceViewModel(repo, unavailable)
                model.editText("今晚19-20在父亲楼有一节选修课")
                model.submit()
                val error = withTimeout(5_000) { model.uiState.first { it.error != null && !it.busy } }
                assertEquals("AI 暂不可用，请稍后重试或使用 + 手动创建", error.error)
                assertEquals("今晚19-20在父亲楼有一节选修课", error.text)
                assertTrue(repo.getEvents(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            }
        }
    }

    @Test fun explicitlyReportedPastRangeSavesSessionAndUndoRemovesIt() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val mapped = QwenToolCallMapper().map("create_record", """{
                "title":"找蜡块",
                "temporal_type":"INTERVAL",
                "start_at":"2026-09-26T09:00:00+08:00",
                "end_at":"2026-09-26T12:00:00+08:00",
                "location":"病理科",
                "note":"补记"
            }""", "今天早上9-12点在病理科找蜡块", VoiceAgentContext(now, zone))
            val ready = (VoiceIntentPolicy.evaluate(mapped, zone) as VoiceIntentDecision.Ready).draft
            assertEquals(VoiceAction.CreateRecord, ready.action)
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(ready)
                as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.records.single()
            assertEquals("病理科", saved.location)
            assertEquals("补记", saved.note)
            assertEquals(Instant.parse("2026-09-26T01:00:00Z").toEpochMilli(), saved.startAtMillis)
            assertEquals(Instant.parse("2026-09-26T04:00:00Z").toEpochMilli(), saved.endAtMillis)
            assertTrue(repo.getEvents(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertTrue(repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
        }
    }

    @Test fun pointRecordDirectlySavesPointAndUndoRemovesIt() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val mapped = QwenToolCallMapper().map("create_record", """{
                "title":"买早饭","temporal_type":"POINT","occurred_at":"2026-09-26T09:30:00+08:00"
            }""", "早上9点半买早饭了", VoiceAgentContext(now, zone))
            val direct = (VoiceIntentPolicy.evaluate(mapped, zone) as VoiceIntentDecision.Ready).draft
            assertEquals(SessionTemporalType.POINT, direct.recordTemporalType)
            assertNull(direct.fields.endAt)
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(direct)
                as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.records.single()
            assertEquals(Instant.parse("2026-09-26T01:30:00Z").toEpochMilli(),
                saved.startAtMillis)
            assertEquals(io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.POINT, saved.temporalType)
            assertNull(saved.endAtMillis)
            assertTrue(repo.getEvents(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertNull(repo.getSession(saved.id))
        }
    }

    @Test fun eventCopyWithoutActualTimeDoesNotWriteRoomSession() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T10:00:00Z")
            val eventId = (repo.upsertEvent(Event(title = "组会",
                dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
                startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60,
                createdAtMillis = 0, updatedAtMillis = 0)) as DomainResult.Success).value
            val draft = QwenToolCallMapper().map("create_record_from_event",
                """{"event_query":"组会","temporal_type":"INTERVAL"}""",
                "把组会记为记录", VoiceAgentContext(now, zone))
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(draft, eventId)
            assertTrue(result is VoiceExecutionResult.Fallback)
            assertTrue(repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            assertNotNull(repo.getEvent(eventId))
        }
    }

    @Test fun completedNapUsesSleepKindClearsClassificationAndUndoDeletesIt() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val mapped = QwenToolCallMapper().map("create_sleep_record", """{
                "sleep_type":"nap","title":"午睡",
                "start_at":"2026-09-26T12:30:00+08:00",
                "end_at":"2026-09-26T13:00:00+08:00",
                "category_hint":"休息"
            }""", "午睡了半小时", VoiceAgentContext(now, zone))
            val ready = (VoiceIntentPolicy.evaluate(mapped, zone) as VoiceIntentDecision.Ready).draft
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(ready)
                as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.records.single()
            assertEquals(SessionKind.NAP, saved.kind)
            assertNull(saved.categoryId)
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertTrue(repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
        }
    }

    @Test fun startNapCreatesOpenSleepAndRespectsActiveSessionConflict() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val start = QwenToolCallMapper().map("start_sleep", """{"sleep_type":"nap"}""",
                "开始午睡", VoiceAgentContext(now, zone))
            val first = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(start)
                as VoiceExecutionResult.Executed
            val saved = first.receipt.undo.after.records.single()
            assertEquals(SessionKind.NAP, saved.kind)
            assertNull(saved.endAtMillis)

            val second = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(start)
            assertTrue(second is VoiceExecutionResult.Fallback)
            assertEquals(1, repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).size)
        }
    }
}

