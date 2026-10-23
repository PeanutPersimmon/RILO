package io.github.peanutpersimmon.rilo.domain.voice

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One transient input attempt, never persisted. Fallback may continue; success can only write once. */
class VoiceExecutionAttempt {
    private val mutex = Mutex()
    private var completed: VoiceExecutionResult.Executed? = null
    suspend fun run(operation: suspend () -> VoiceExecutionResult): VoiceExecutionResult = mutex.withLock {
        completed ?: operation().also { if (it is VoiceExecutionResult.Executed) completed = it }
    }
}
