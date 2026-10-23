package io.github.peanutpersimmon.rilo.domain.voice.agent

import io.github.peanutpersimmon.rilo.domain.voice.VoiceActionDraft
import java.time.Instant
import java.time.ZoneId

data class QwenCredentials(
    val apiKey: String,
    val workspaceId: String,
)

interface QwenCredentialStore {
    suspend fun getCredentials(): QwenCredentials?
    suspend fun saveCredentials(apiKey: String, workspaceId: String)
    suspend fun clear()
}

sealed interface QwenConfigState {
    data class Configured(val credentials: QwenCredentials) : QwenConfigState
    data object NotConfigured : QwenConfigState
    data object Unavailable : QwenConfigState
}

interface QwenConfigProvider {
    suspend fun getConfiguration(): QwenConfigState
}

data class VoiceAgentContext(
    val now: Instant = Instant.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

data class VoiceAgentToolOutput(
    val success: Boolean,
    val result: String? = null,
    val title: String? = null,
    val reason: String? = null,
)

interface VoiceAgentPendingCall {
    val callId: String
    val toolName: String
    val arguments: String
    suspend fun complete(output: VoiceAgentToolOutput)
    fun cancel()
}

sealed interface VoiceAgentResult {
    data class Action(val draft: VoiceActionDraft, val call: VoiceAgentPendingCall) : VoiceAgentResult
    data class NoAction(val message: String = "没有识别到可执行的日程、记录或睡眠") : VoiceAgentResult
    data class Unavailable(val reason: VoiceAgentUnavailableReason, val message: String) : VoiceAgentResult
    data class InvalidToolCall(val message: String) : VoiceAgentResult
}

enum class VoiceAgentUnavailableReason { NotConfigured, NetworkOrService }

interface VoiceAgentEngine {
    suspend fun executeText(text: String, context: VoiceAgentContext): VoiceAgentResult
    suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext): VoiceAgentResult
}
