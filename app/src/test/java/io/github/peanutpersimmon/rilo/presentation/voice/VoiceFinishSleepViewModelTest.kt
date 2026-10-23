package io.github.peanutpersimmon.rilo.presentation.voice

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.voice.VoiceClarification
import io.github.peanutpersimmon.rilo.domain.voice.agent.*
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceFinishSleepViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun noActiveSleepClarifiesAndNeverStopsAnActivity() = runTest {
        val now = Instant.parse("2026-10-15T12:00:00Z")
        val activity = Session(id = 7, title = "看论文", startAtMillis = now.minusSeconds(3600).toEpochMilli(),
            createdAtMillis = 0, updatedAtMillis = 0)
        val repository = Proxy.newProxyInstance(RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "observeActiveSession" -> flowOf(activity)
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository
        val draft = QwenToolCallMapper().map("finish_sleep", "{}", "刚睡醒",
            VoiceAgentContext(now, ZoneId.of("Asia/Shanghai")))
        var completion: VoiceAgentToolOutput? = null
        val pending = object : VoiceAgentPendingCall {
            override val callId = "call-1"
            override val toolName = "finish_sleep"
            override val arguments = "{}"
            override suspend fun complete(output: VoiceAgentToolOutput) { completion = output }
            override fun cancel() = Unit
        }
        val agent = object : VoiceAgentEngine {
            override suspend fun executeText(text: String, context: VoiceAgentContext) = VoiceAgentResult.Action(draft, pending)
            override suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext) = VoiceAgentResult.NoAction()
        }
        val viewModel = VoiceViewModel(repository, agent)
        viewModel.editText("刚睡醒")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(VoiceClarification.NoActiveSleep, viewModel.uiState.value.clarification)
        assertEquals(false, completion?.success)
        assertNull(activity.endAtMillis)
    }
}
