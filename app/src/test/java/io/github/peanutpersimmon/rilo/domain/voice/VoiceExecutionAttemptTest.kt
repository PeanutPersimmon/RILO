package io.github.peanutpersimmon.rilo.domain.voice

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VoiceExecutionAttemptTest {
    @Test fun concurrentRequestsShareOneSuccessfulExecution() = runBlocking {
        val attempt = VoiceExecutionAttempt()
        var writes = 0
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val result = VoiceExecutionResult.Executed(VoiceExecutionReceipt("已创建「选修课」", VoiceUndo(VoiceDataSnapshot(), VoiceDataSnapshot())))
        val first = async { attempt.run { writes++; entered.complete(Unit); finish.await(); result } }
        entered.await()
        val second = async { attempt.run { writes++; result } }
        finish.complete(Unit)
        assertSame(first.await(), second.await())
        assertEquals(1, writes)
    }
    @Test fun fallbackMayBeCorrectedAndRetried() = runBlocking {
        val attempt = VoiceExecutionAttempt()
        assertTrue(attempt.run { VoiceExecutionResult.Fallback(VoicePrepared(error = "请补充")) } is VoiceExecutionResult.Fallback)
        assertTrue(attempt.run { VoiceExecutionResult.Executed(VoiceExecutionReceipt("已创建「组会」", VoiceUndo(VoiceDataSnapshot(), VoiceDataSnapshot()))) } is VoiceExecutionResult.Executed)
    }
}
