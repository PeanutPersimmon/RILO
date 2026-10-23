package io.github.peanutpersimmon.rilo.presentation.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceAgentDiagnosticsTest {
    @Test fun rawMappingAndValidationStaySeparateForCurrentAttempt() {
        VoiceAgentDiagnosticsStore.begin()
        VoiceAgentDiagnosticsStore.rawArguments("""{"start_at":"2026-09-24T12:00:00+08:00"}""")
        VoiceAgentDiagnosticsStore.mappedAction("CreateEvent")
        VoiceAgentDiagnosticsStore.validationResult("success")
        val text = VoiceAgentDiagnosticsStore.state.value.asText()
        assertTrue(text.contains("rawArguments ="))
        assertTrue(text.contains("mappedAction = CreateEvent"))
        assertTrue(text.contains("validationResult = success"))
        VoiceAgentDiagnosticsStore.begin()
        assertFalse(VoiceAgentDiagnosticsStore.state.value.asText().contains("rawArguments ="))
    }

    @Test fun decisionTraceShowsStagesAndRemovesSensitiveArgumentKeys() {
        VoiceAgentDiagnosticsStore.begin()
        VoiceAgentDiagnosticsStore.source("  下午一点  到两点睡了一会  ")
        VoiceAgentDiagnosticsStore.tool("create_sleep_record")
        VoiceAgentDiagnosticsStore.rawArguments("""{"start_at":"2026-10-15T13:00:00+08:00","api_key":"secret-key","authorization":"Bearer abc","audio":"bytes"}""")
        VoiceAgentDiagnosticsStore.mappedAction("CreateSleepRecord")
        VoiceAgentDiagnosticsStore.policyResult("Ready")
        VoiceAgentDiagnosticsStore.decision("planner", "Create NAP")
        VoiceAgentDiagnosticsStore.decision("executionAction", "CreateSleepRecord")
        VoiceAgentDiagnosticsStore.decision("savedDomainType", "SLEEP")
        val text = VoiceAgentDiagnosticsStore.state.value.asText()
        for (expected in listOf("source = 下午一点 到两点睡了一会", "tool = create_sleep_record",
                "mappedAction = CreateSleepRecord", "policy = Ready", "planner = Create NAP",
                "savedDomainType = SLEEP")) assertTrue(text.contains(expected))
        for (secret in listOf("secret-key", "Bearer abc", "bytes", "api_key", "authorization"))
            assertFalse(text.contains(secret))
        VoiceAgentDiagnosticsStore.decision("reason", "NO_ACTIVE_SLEEP")
        assertTrue(VoiceAgentDiagnosticsStore.state.value.asText().contains("reason = NO_ACTIVE_SLEEP"))
    }
}
