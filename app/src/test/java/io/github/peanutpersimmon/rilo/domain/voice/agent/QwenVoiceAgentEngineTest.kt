package io.github.peanutpersimmon.rilo.domain.voice.agent

import com.google.gson.JsonParser
import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenAudioClient
import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenRemoteResult
import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenTextClient
import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenVoiceAgentEngine
import io.github.peanutpersimmon.rilo.domain.voice.VoiceAction
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QwenVoiceAgentEngineTest {
    private val context = VoiceAgentContext(Instant.parse("2026-09-24T02:30:00Z"), ZoneId.of("Asia/Shanghai"))
    private val config = object : QwenConfigProvider {
        override suspend fun getConfiguration() = QwenConfigState.Configured(QwenCredentials("test-only", "test-workspace"))
    }

    @Test fun fakeRealtimeEventsMapDoneArgumentsAndReturnFunctionOutput() = runBlocking {
        val outputs = mutableListOf<String>()
        val client = object : QwenTextClient {
            override suspend fun executeText(credentials: QwenCredentials, text: String, context: VoiceAgentContext): QwenRemoteResult {
                assertEquals("test-workspace", credentials.workspaceId)
                assertEquals("今晚有选修课", text)
                val events = listOf(
                    """{"type":"session.created"}""",
                    """{"type":"session.updated"}""",
                    """{"type":"response.function_call_arguments.done","call_id":"call-1","name":"create_event","arguments":"{\"title\":\"选修课\",\"temporal_type\":\"DATE\",\"date\":\"2026-09-24\"}"}"""
                ).map { JsonParser.parseString(it).asJsonObject }
                assertEquals("session.created", events[0].get("type").asString)
                assertEquals("session.updated", events[1].get("type").asString)
                val done = events[2]
                return QwenRemoteResult.ToolCall(done.get("call_id").asString, done.get("name").asString,
                    done.get("arguments").asString, { outputs += it }, {})
            }
        }
        val result = QwenVoiceAgentEngine(config, client).executeText("今晚有选修课", context)
        assertTrue(result is VoiceAgentResult.Action)
        val action = result as VoiceAgentResult.Action
        assertEquals(VoiceAction.CreateEvent, action.draft.action)
        assertEquals("选修课", action.draft.targetText)
        action.call.complete(VoiceAgentToolOutput(true, result = "created", title = "选修课"))
        val output = JsonParser.parseString(outputs.single()).asJsonObject
        assertTrue(output.get("success").asBoolean)
        assertEquals("created", output.get("result").asString)
    }

    @Test fun toolCallWithInvalidJsonNeverBecomesUnavailable() = runBlocking {
        val outputs = mutableListOf<String>()
        val client = object : QwenTextClient {
            override suspend fun executeText(credentials: QwenCredentials, text: String, context: VoiceAgentContext) =
                QwenRemoteResult.ToolCall("call-2", "create_event", "{", { outputs += it }, {})
        }
        val result = QwenVoiceAgentEngine(config, client).executeText("今晚有选修课", context)
        assertTrue(result is VoiceAgentResult.InvalidToolCall)
        assertEquals("invalid_arguments", JsonParser.parseString(outputs.single()).asJsonObject.get("reason").asString)
    }

    @Test fun audioToolCallUsesTheSameMapperAsText() = runBlocking {
        val turn = QwenAudioTurnController()
        turn.append(byteArrayOf(1, 2))
        turn.finishRecording()
        val calls = mutableListOf<String>()
        val fakeAudio = object : QwenAudioClient {
            override suspend fun executeAudio(credentials: QwenCredentials, turn: QwenAudioTurnController,
                context: VoiceAgentContext): QwenRemoteResult {
                turn.attach(object : AudioChunkSink {
                    override fun append(pcm: ByteArray): Boolean { calls += "append"; return true }
                    override fun commit(): Boolean { calls += "commit"; return true }
                    override fun responseCreate(): Boolean { calls += "response"; return true }
                    override fun clear() { calls += "clear" }
                    override fun close() { calls += "close" }
                })
                turn.sessionUpdated()
                turn.bufferCommitted()
                return QwenRemoteResult.ToolCall("audio-1", "create_event",
                    """{"title":"开会","temporal_type":"POINT","start_at":"2026-09-24T15:00:00+08:00"}""", { calls += "output" }, {})
            }
        }
        val result = QwenVoiceAgentEngine(config, audioClient = fakeAudio).executeAudio(turn, context)
        assertTrue(result is VoiceAgentResult.Action)
        val action = result as VoiceAgentResult.Action
        assertEquals(VoiceAction.CreateEvent, action.draft.action)
        assertEquals(listOf("append", "commit", "response"), calls)
        action.call.complete(VoiceAgentToolOutput(true, result = "created", title = "开会"))
        assertEquals("output", calls.last())
    }

    @Test fun preToolFailureIsUnavailableForLocalFallback() = runBlocking {
        var calls = 0
        val client = object : QwenTextClient {
            override suspend fun executeText(credentials: QwenCredentials, text: String, context: VoiceAgentContext): QwenRemoteResult {
                calls++
                return QwenRemoteResult.Failed("网络不可用")
            }
        }
        val result = QwenVoiceAgentEngine(config, client).executeText("交论文", context)
        assertTrue(result is VoiceAgentResult.Unavailable)
        assertEquals(2, calls)
    }
}
