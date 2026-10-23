package io.github.peanutpersimmon.rilo.domain.voice.agent

import io.github.peanutpersimmon.rilo.data.remote.qwen.QwenAgentProtocol
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class QwenAudioTurnControllerTest {
    private class Sink : AudioChunkSink {
        val events = mutableListOf<String>()
        override fun append(pcm: ByteArray): Boolean { events += "append:" + String(pcm); return true }
        override fun commit(): Boolean { events += "commit"; return true }
        override fun responseCreate(): Boolean { events += "response.create"; return true }
        override fun clear() { events += "clear" }
        override fun close() { events += "close" }
    }

    @Test fun threeChunksCommitAndResponseFollowServerAcknowledgment() {
        val turn = QwenAudioTurnController()
        val sink = Sink()
        turn.attach(sink)
        turn.sessionUpdated()
        listOf("A", "B", "C").forEach { turn.append(it.toByteArray()) }
        turn.finishRecording()
        assertEquals(listOf("append:A", "append:B", "append:C", "commit"), sink.events)
        turn.bufferCommitted()
        turn.bufferCommitted()
        assertEquals(listOf("append:A", "append:B", "append:C", "commit", "response.create"), sink.events)
    }

    @Test fun largeReadIsSplitIntoSmallPcmAppends() {
        val sizes = mutableListOf<Int>()
        val turn = QwenAudioTurnController()
        turn.attach(object : AudioChunkSink {
            override fun append(pcm: ByteArray): Boolean { sizes += pcm.size; return true }
            override fun commit() = true
            override fun responseCreate() = true
            override fun clear() = Unit
            override fun close() = Unit
        })
        turn.sessionUpdated()
        turn.append(ByteArray(6_402))
        assertEquals(listOf(3_200, 3_200, 2), sizes)
    }

    @Test fun queuedPcmFlushesInOrderWhenSessionBecomesReady() {
        val turn = QwenAudioTurnController()
        val sink = Sink()
        turn.append("A".toByteArray())
        turn.append("B".toByteArray())
        turn.attach(sink)
        assertEquals(2, turn.bufferedBytes)
        assertTrue(sink.events.isEmpty())
        turn.sessionUpdated()
        assertEquals(listOf("append:A", "append:B"), sink.events)
        assertEquals(0, turn.bufferedBytes)
    }

    @Test fun quickReleaseBeforeReadyStillFlushesThenCommits() {
        val turn = QwenAudioTurnController()
        val sink = Sink()
        turn.append("A".toByteArray())
        turn.finishRecording()
        turn.attach(sink)
        turn.sessionUpdated()
        assertEquals(listOf("append:A", "commit"), sink.events)
        turn.bufferCommitted()
        assertEquals("response.create", sink.events.last())
    }

    @Test fun cancelClearsBufferAndNeverCommitsOrRequestsResponse() {
        val turn = QwenAudioTurnController()
        val sink = Sink()
        turn.attach(sink)
        turn.sessionUpdated()
        turn.append("A".toByteArray())
        turn.cancel()
        turn.bufferCommitted()
        assertEquals(listOf("append:A", "clear", "close"), sink.events)
        assertEquals(0, turn.bufferedBytes)
    }

    @Test fun emptyAndTooLongAudioFailWithoutCommit() {
        val empty = QwenAudioTurnController()
        val emptySink = Sink()
        empty.attach(emptySink)
        empty.sessionUpdated()
        assertEquals(AudioTurnIssue.Empty, empty.finishRecording())
        assertFalse(emptySink.events.contains("commit"))

        val long = QwenAudioTurnController(maxBytes = 4)
        val sink = Sink()
        long.attach(sink)
        assertNull(long.append(byteArrayOf(1, 2, 3, 4)))
        assertEquals(AudioTurnIssue.TooLong, long.append(byteArrayOf(5, 6)))
        assertTrue(long.isCancelled)
        assertEquals(0, long.bufferedBytes)
    }

    @Test fun audioSessionUsesSameToolsInstructionsAndTextOnlyOutput() {
        val context = VoiceAgentContext(Instant.parse("2026-09-24T02:30:00Z"), ZoneId.of("Asia/Shanghai"))
        val text = QwenAgentProtocol.sessionUpdate(context)
        val audio = QwenAgentProtocol.sessionUpdate(context, audioInput = true)
        val textSession = text.getAsJsonObject("session")
        val audioSession = audio.getAsJsonObject("session")
        assertEquals(textSession.get("instructions"), audioSession.get("instructions"))
        assertEquals(textSession.get("tools"), audioSession.get("tools"))
        assertEquals("text", audioSession.getAsJsonArray("modalities")[0].asString)
        assertTrue(audioSession.get("turn_detection").isJsonNull)
        assertEquals(16000, audioSession.getAsJsonObject("audio").getAsJsonObject("input")
            .getAsJsonObject("format").get("sample_rate").asInt)
        val pcm = byteArrayOf(1, 2, 3, 4)
        val event = QwenAgentProtocol.audioAppend(pcm)
        assertArrayEquals(pcm, Base64.getDecoder().decode(event.get("audio").asString))
        assertEquals("input_audio_buffer.commit", QwenAgentProtocol.audioCommit().get("type").asString)
    }
}
