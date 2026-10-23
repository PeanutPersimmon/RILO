package io.github.peanutpersimmon.rilo.presentation.voice

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceAgentDiagnostics(
    val trace: List<String> = emptyList(),
    val normalizedSourceText: String? = null,
    val rawToolName: String? = null,
    val rawArguments: String? = null,
    val mappedAction: String? = null,
    val policyResult: String? = null,
    val plannerResult: String? = null,
    val executionAction: String? = null,
    val savedDomainType: String? = "NONE",
    val reason: String? = null,
    val validationResult: String? = null,
) {
    fun asText(): String = buildString {
        appendLine("RILO Qwen Agent diagnostics (current attempt only)")
        trace.forEach(::appendLine)
        normalizedSourceText?.let { appendLine("source = $it") }
        rawToolName?.let { appendLine("tool = $it") }
        rawArguments?.let { appendLine("rawArguments = $it") }
        mappedAction?.let { appendLine("mappedAction = $it") }
        policyResult?.let { appendLine("policy = $it") }
        plannerResult?.let { appendLine("planner = $it") }
        executionAction?.let { appendLine("executionAction = $it") }
        savedDomainType?.let { appendLine("savedDomainType = $it") }
        reason?.let { appendLine("reason = $it") }
        validationResult?.let { appendLine("validationResult = $it") }
    }
}

/** Memory-only diagnostics. Credentials are never accepted by this API. */
object VoiceAgentDiagnosticsStore {
    private val current = MutableStateFlow(VoiceAgentDiagnostics())
    val state = current.asStateFlow()
    fun begin() { current.value = VoiceAgentDiagnostics() }
    fun event(value: String) { current.update { it.copy(trace = (it.trace + value).takeLast(40)) } }
    fun source(value: String) { current.update { it.copy(normalizedSourceText = redact(value.trim().replace(Regex("\\s+"), " ")).take(300)) } }
    fun tool(value: String) { current.update { it.copy(rawToolName = value) } }
    fun rawArguments(value: String) { current.update { it.copy(rawArguments = safeArguments(value), mappedAction = null, validationResult = null) } }
    fun mappedAction(value: String) { current.update { it.copy(mappedAction = value) } }
    fun policyResult(value: String) { current.update { it.copy(policyResult = value) } }
    fun reason(value: String) { current.update { it.copy(reason = value) } }
    fun decision(stage: String, value: String) { current.update { state -> when (stage) {
        "planner" -> state.copy(plannerResult = value)
        "executionAction" -> state.copy(executionAction = value)
        "savedDomainType" -> state.copy(savedDomainType = value)
        "reason" -> state.copy(reason = value)
        else -> state
    } } }
    fun validationResult(value: String) { current.update { it.copy(validationResult = value) } }

    private val allowedArgumentKeys = setOf("title", "temporal_type", "date", "start_at", "end_at",
        "occurred_at", "actual_at", "actual_start_at", "actual_end_at", "sleep_type", "reason",
        "question", "known_date", "record_title", "event_query", "event_date", "target_query",
        "target_date", "target_start_at", "location", "note", "category_hint", "reminder_offset_minutes",
        "reminder_offset_unresolved",
        "clear_fields")
    private fun safeArguments(raw: String): String = try {
        val input = JsonParser.parseString(raw).asJsonObject
        val safe = JsonObject()
        input.entrySet().filter { it.key in allowedArgumentKeys }.forEach { (key, value) ->
            safe.add(key, value)
        }
        redact(safe.toString()).take(2000)
    } catch (_: Exception) { "<invalid JSON>" }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(api[_ -]?key|token|authorization|password|secret)\\s*[:=]\\s*\\S+"), "[redacted]")
        .replace(Regex("(?i)Bearer\\s+\\S+"), "[redacted]")
        .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "[redacted]")
}
